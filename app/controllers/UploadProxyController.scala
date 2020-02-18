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

import java.nio.charset.StandardCharsets.UTF_8

import akka.stream.IOResult
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString
import javax.inject.Inject
import org.apache.http.client.utils.URIBuilder
import play.api.http
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.Json
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.mvc.MultipartFormData.{DataPart, FilePart}
import play.api.mvc._
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.xml.Elem

class UploadProxyController @Inject()(wsClient: WSClient)(implicit ec: ExecutionContext) extends BaseController {

  private val missingRedirectUrl = Results.BadRequest(
    Json.parse("""{"message":"Could not find error_action_redirect field in request"}""")
  )
  private val missingKey = Results.BadRequest(
    Json.parse("""{"message":"Could not find key field in request"}""")
  )
  private val badRedirectUrl = Results.BadRequest(
    Json.parse("""{"message":"Unable to build valid redirect URL for error action"}""")
  )

  def upload(): Action[MultipartFormData[TemporaryFile]] = Action.async(parse.multipartFormData) { implicit request =>
    extractRedirectWithKey(request.body).fold(
      errorResult => Future.successful(errorResult),
      redirectUrl => {
        val response = wsClient
          .url(routes.UploadController.upload().absoluteURL())
          .withFollowRedirects(follow = false)
          .post(Source(dataParts(request.body.dataParts) ++ fileParts(request.body.files)))
        response.map {
          case r if r.status >= 200 && r.status < 400 => toResult(r)
          case r                                      => redirect(redirectUrl, r.body)
        }
      }
    )
  }

  private def extractRedirectWithKey(multiPartFormData: MultipartFormData[TemporaryFile]): Either[Result, String] =
    for {
      redirectUrl <- extractErrorActionRedirect(multiPartFormData).right
      key <- extractKey(multiPartFormData).right
      errorActionRedirectUrl <- buildErrorActionRedirectUrl(redirectUrl, key).right
    } yield errorActionRedirectUrl
  
  private def extractErrorActionRedirect(multiPartFormData: MultipartFormData[TemporaryFile]): Either[Result, String] =
    extractSingletonFormValue("error_action_redirect", multiPartFormData).toRight(left = missingRedirectUrl)

  private def extractKey(multiPartFormData: MultipartFormData[TemporaryFile]): Either[Result, String] =
    extractSingletonFormValue("key", multiPartFormData).toRight(left = missingKey)

  private def extractSingletonFormValue(key: String, multiPartFormData: MultipartFormData[TemporaryFile]): Option[String] =
    multiPartFormData.dataParts
      .get(key)
      .flatMap(_.headOption)

  private def buildErrorActionRedirectUrl(redirectUrl: String, key: String): Either[Result, String] =
    Try {
      new URIBuilder(redirectUrl, UTF_8).addParameter("key", key).build().toASCIIString
    }.toOption.toRight(left = badRedirectUrl)

  private def getErrorParameter(elemType: String, xml: Elem): Option[(String, String)] =
    (xml \ elemType).headOption.map(node => s"error$elemType" -> node.text)

  private def errorParamsList(body: String): List[(String, String)] =
    Try(scala.xml.XML.loadString(body)).toOption.toList.flatMap { xml =>
      val requestId = getErrorParameter("RequestId", xml)
      val resource  = getErrorParameter("Resource", xml)
      val message   = getErrorParameter("Message", xml)
      val code      = getErrorParameter("Code", xml)
      List(code, message, resource, requestId).flatten
    }

  private def redirect(url: String, body: String): Result = {
    val errors = errorParamsList(body)
    val urlBuilder = errors.foldLeft(new URIBuilder(url)) { (urlBuilder, error) =>
      urlBuilder.addParameter(error._1, error._2)
    }
    Redirect(urlBuilder.build().toASCIIString, http.Status.SEE_OTHER)
  }

  private def dataParts(dataPart: Map[String, Seq[String]]): List[DataPart] =
    dataPart.flatMap { case (header, body) => body.map(DataPart(header, _)) }.toList

  private def fileParts(filePart: Seq[FilePart[TemporaryFile]]): List[FilePart[Source[ByteString, Future[IOResult]]]] =
    filePart.map(d => FilePart(d.key, d.filename, d.contentType, FileIO.fromPath(d.ref.file.toPath))).toList

  private def toResult(response: WSResponse): Result = {
    val headers = response.allHeaders.toList.flatMap { case (h, v) => v.map((h, _)) }
    Results.Status(response.status)(response.body).withHeaders(headers: _*)
  }

}
