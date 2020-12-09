/*
 * Copyright 2020 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers

import java.net.URI
import java.nio.charset.StandardCharsets.UTF_8
import akka.stream.IOResult
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString
import controllers.UploadProxyController.ErrorResponseHandler.proxyErrorResponse

import javax.inject.Inject
import org.apache.http.client.utils.URIBuilder
import play.api.Logger
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.Json
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.mvc.MultipartFormData.{DataPart, FilePart}
import play.api.mvc._
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import utils.MultipartFormDataSummaries.{summariseDataParts, summariseFileParts}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.xml.Elem

class UploadProxyController @Inject()(wsClient: WSClient, cc: ControllerComponents)
                                     (implicit ec: ExecutionContext) extends BackendController(cc) {

  import UploadProxyController._

  private val logger = Logger(this.getClass)

  def upload(): Action[MultipartFormData[TemporaryFile]] = Action.async(parse.multipartFormData) { implicit request =>
    val body = request.body
    logger.debug(s"Upload form contains dataParts=${summariseDataParts(body.dataParts)} and fileParts=${summariseFileParts(body.files)}")
    MultipartFormExtractor.extractErrorAction(body).fold(
      errorResult => Future.successful(errorResult),
      errorAction => {
        val response = wsClient
          .url(routes.UploadController.upload().absoluteURL())
          .withFollowRedirects(follow = false)
          .post(Source(dataParts(body.dataParts) ++ fileParts(body.files)))
        response.foreach(r => logger.debug(s"Upload response has status=[${r.status}], headers=[${r.headers}], body=[${r.body}]"))
        response.map {
          case r if r.status >= 200 && r.status < 400 => toSuccessResult(r)
          case r                                      => proxyErrorResponse(errorAction, r.status, r.body, r.headers)
        }
      }
    )
  }

  private def dataParts(dataPart: Map[String, Seq[String]]): List[DataPart] =
    dataPart.flatMap { case (header, body) => body.map(DataPart(header, _)) }.toList

  private def fileParts(filePart: Seq[FilePart[TemporaryFile]]): List[FilePart[Source[ByteString, Future[IOResult]]]] =
    filePart.map(d => FilePart(d.key, d.filename, d.contentType, FileIO.fromPath(d.ref.path))).toList

  private def toSuccessResult(response: WSResponse): Result =
    Results.Status(response.status)(response.body).withHeaders(asTuples(response.headers): _*)
}

private object UploadProxyController {

  case class ErrorAction(redirectUrl: Option[String], key: String)

  private val KeyName = "key"

  object MultipartFormExtractor {

    private val missingKey = Results.BadRequest(
      Json.parse("""{"message":"Could not find key field in request"}""")
    )
    private val badRedirectUrl = Results.BadRequest(
      Json.parse("""{"message":"Unable to build valid redirect URL for error action"}""")
    )

    def extractErrorAction(multipartFormData: MultipartFormData[TemporaryFile]): Either[Result, ErrorAction] = {
      val maybeErrorActionRedirect = extractErrorActionRedirect(multipartFormData)
      extractKey(multipartFormData).flatMap { key =>
        maybeErrorActionRedirect.map { errorActionRedirect =>
          validateErrorActionRedirectUrl(errorActionRedirect, key).map(_ => ErrorAction(maybeErrorActionRedirect, key))
        }.getOrElse(Right(ErrorAction(None, key)))
      }
    }

    private def extractErrorActionRedirect(multiPartFormData: MultipartFormData[TemporaryFile]): Option[String] =
      extractSingletonFormValue("error_action_redirect", multiPartFormData)

    private def extractKey(multiPartFormData: MultipartFormData[TemporaryFile]): Either[Result, String] =
      extractSingletonFormValue(KeyName, multiPartFormData).toRight(left = missingKey)

    private def extractSingletonFormValue(key: String, multiPartFormData: MultipartFormData[TemporaryFile]): Option[String] =
      multiPartFormData.dataParts
        .get(key)
        .flatMap(_.headOption)

    private def validateErrorActionRedirectUrl(redirectUrl: String, key: String): Either[Result, URI] =
      Try {
        new URIBuilder(redirectUrl, UTF_8).addParameter("key", key).build()
      }.toOption.toRight(left = badRedirectUrl)
  }

  object ErrorResponseHandler {

    def proxyErrorResponse(errorAction: ErrorAction,
                           statusCode: Int,
                           xmlResponseBody: String,
                           responseHeaders: Map[String, Seq[String]]): Result = {
      val errorFields = toErrorFields(errorAction.key, xmlResponseBody)
      val exposableHeaders = responseHeaders.filter { case (name, _ ) => isExposableResponseHeader(name) }
      errorAction.redirectUrl.fold(ifEmpty = jsonResult(statusCode, errorFields)) { redirectUrl =>
        redirectResult(redirectUrl, queryParams = errorFields)
      }.withHeaders(asTuples(exposableHeaders): _*)
    }

    /*
     * This is a dummy placeholder to draw attention to the fact that filtering of error response headers is
     * required in the real implementation.  We currently retain only CORS-related headers and custom Amazon
     * headers.  The implementation in this stub differs.  This stub has a CORS filter (the real implementation
     * does not), and our UploadController does not add any fake Amazon headers - so we actually have nothing
     * to do here ...
     */
    private def isExposableResponseHeader(name: String): Boolean = false

    private def jsonResult(statusCode: Int, fields: Seq[(String, String)]): Result =
      Results.Status(statusCode)(Json.toJsObject(fields.toMap))

    private def redirectResult(url: String, queryParams: Seq[(String, String)]): Result = {
      val urlBuilder = queryParams.foldLeft(new URIBuilder(url)) { (urlBuilder, param) =>
        urlBuilder.addParameter(param._1, param._2)
      }
      Results.SeeOther(urlBuilder.build().toASCIIString)
    }

    private def toErrorFields(key: String, xmlResponseBody: String): Seq[(String, String)] =
      (KeyName -> key) +: xmlErrorFields(xmlResponseBody)

    private def xmlErrorFields(xmlBody: String): Seq[(String, String)] =
      Try(scala.xml.XML.loadString(xmlBody)).toOption.toList.flatMap { xml =>
        val requestId = makeOptionalField("RequestId", xml)
        val resource  = makeOptionalField("Resource", xml)
        val message   = makeOptionalField("Message", xml)
        val code      = makeOptionalField("Code", xml)
        Seq(code, message, resource, requestId).flatten
      }

    private def makeOptionalField(elemType: String, xml: Elem): Option[(String, String)] =
      (xml \ elemType).headOption.map(node => s"error$elemType" -> node.text)
  }

  def asTuples(values: Map[String, Seq[String]]): Seq[(String, String)] =
    values.toList.flatMap { case (h, v) => v.map((h, _)) }
}