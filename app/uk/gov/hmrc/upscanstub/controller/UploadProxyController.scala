/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.upscanstub.controller

import org.apache.pekko.stream.IOResult
import org.apache.pekko.stream.scaladsl.{FileIO, Source}
import org.apache.pekko.util.ByteString
import play.api.Logger
import play.api.http.Status
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.Json
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.mvc.MultipartFormData.{DataPart, FilePart}
import play.api.mvc._
import sttp.model.Uri.UriContext
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.upscanstub.controller.UploadProxyController.ErrorResponseHandler.{errorResponse, proxyErrorResponse}
import uk.gov.hmrc.upscanstub.controller.UploadProxyController.TemporaryFilePart.partitionTrys
import uk.gov.hmrc.upscanstub.util.MultipartFormDataSummaries.{summariseDataParts, summariseFileParts}

import java.net.URI
import java.nio.file.{Files, Path}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import scala.xml.Elem

class UploadProxyController @Inject()(
  wsClient: WSClient,
  cc      : ControllerComponents
)(using
  ExecutionContext
) extends BackendController(cc):

  import UploadProxyController._

  private val logger = Logger(this.getClass)

  lazy val upload: Action[MultipartFormData[TemporaryFile]] =
    Action.async(parse.multipartFormData): request =>
      given RequestHeader = request
      val body = request.body
      logger.debug(
        s"Upload form contains dataParts=${summariseDataParts(body.dataParts)} and fileParts=${summariseFileParts(body.files)}")
      MultipartFormExtractor
        .extractErrorAction(body)
        .fold(
          errorResult =>
            Future.successful(errorResult),
          errorAction =>
            val (fileAdoptionFailures, fileAdoptionSuccesses) =
              partitionTrys:
                body.files.map: filePart =>
                  for
                    adoptedFilePart <- TemporaryFilePart.adoptFile(filePart)
                    _               =  logger.debug(s"Moved TemporaryFile for Key [${errorAction.key}] from [${filePart.ref.path}] to [${adoptedFilePart.ref}]")
                  yield adoptedFilePart

            val futResult =
              fileAdoptionFailures.headOption.fold(
                proxyRequest(
                  errorAction,
                  body = Source(dataParts(body.dataParts) ++ fileAdoptionSuccesses.map(TemporaryFilePart.toUploadSource))
                )
              )(errorMessage => Future.successful(errorResponse(errorAction, errorMessage)))

            futResult.onComplete: _ =>
              Future:
                fileAdoptionSuccesses.foreach: filePart =>
                  TemporaryFilePart
                    .deleteFile(filePart)
                    .fold(
                      err      => logger.warn(s"Failed to delete TemporaryFile for Key [${errorAction.key}] at [${filePart.ref}]", err),
                      didExist => if didExist then logger.debug(s"Deleted TemporaryFile for Key [${errorAction.key}] at [${filePart.ref}]")
                    )

            futResult
        )

  private def dataParts(dataPart: Map[String, Seq[String]]): List[DataPart] =
    dataPart.flatMap { (header, body) => body.map(DataPart(header, _)) }.toList

  private def proxyRequest(
    errorAction: ErrorAction,
    body: Source[MultipartFormData.Part[Source[ByteString, _]], _]
  )(using
    RequestHeader
  ): Future[Result] =
    for
      response <- wsClient
                   .url(routes.UploadController.upload.absoluteURL())
                   .withFollowRedirects(follow = false)
                   .post(body)
      _        =  logger.debug:
                    s"Upload response for Key=[${errorAction.key}] has status=[${response.status}], "
                      + s"headers=[${response.headers}], body=[${response.body}]"
    yield
      response match
        case r if r.status >= 200 && r.status < 400 => toSuccessResult(r)
        case r                                      => proxyErrorResponse(errorAction, r.status, r.body, forScala2_13(r.headers))

  private def toSuccessResult(response: WSResponse): Result =
    Results.Status(response.status)(response.body).withHeaders(asTuples(forScala2_13(response.headers)): _*)

  // play returns scala.collection.Seq, but default for Scala 2.13 is scala.collection.immutable.Seq
  private def forScala2_13(m: Map[String, scala.collection.Seq[String]]): Map[String, Seq[String]] =
    // `m.mapValues(_.toSeq).toMap` by itself strips the ordering away
    scala.collection.immutable.TreeMap[String, Seq[String]]()(scala.math.Ordering.comparatorToOrdering(String.CASE_INSENSITIVE_ORDER)) ++ m.view.mapValues(_.toSeq)

end UploadProxyController

private object UploadProxyController:

  case class ErrorAction(redirectUrl: Option[String], key: String)

  private val KeyName = "key"

  object TemporaryFilePart:
    private val AdoptedFileSuffix = ".out"

    def partitionTrys[A](trys: Seq[Try[A]]): (Seq[String], Seq[A]) =
      val failureResults = trys.collect { case Failure(err) => s"${err.getClass.getSimpleName}: ${err.getMessage}" }
      val successResults = trys.collect { case Success(a)   => a }
      (failureResults, successResults)

    /*
     * Play creates a TemporaryFile as the MultipartFormData is parsed.
     * The file is "owned" by Play, is scoped to the request, and is subject to being deleted by a finalizer thread.
     * We "adopt" the file by moving it.
     * This gives us control of its lifetime at the expense of taking responsibility for cleaning it up.  If our
     * cleanup fails, another cleanup will be attempted on shutDown by virtue of the fact that we have not moved
     * the file outside of the playtempXXX folder.
     *
     * See: https://www.playframework.com/documentation/2.7.x/ScalaFileUpload#Uploading-files-in-a-form-using-multipart/form-data
     * See: play.api.libs.Files$DefaultTemporaryFileCreator's FinalizableReferenceQueue & stopHook
     */
    def adoptFile(filePart: FilePart[TemporaryFile]): Try[FilePart[Path]] =
      val inPath  = filePart.ref.path
      val outPath = inPath.resolveSibling(inPath.getFileName.toString + AdoptedFileSuffix)
      Try(filePart.copy(ref = filePart.ref.atomicMoveWithFallback(outPath), refToBytes = _ => None))

    def toUploadSource(filePart: FilePart[Path]): FilePart[Source[ByteString, Future[IOResult]]] =
      filePart.copy(ref = FileIO.fromPath(filePart.ref), refToBytes = _ => None)

    def deleteFile(filePart: FilePart[Path]): Try[Boolean] =
      Try(Files.deleteIfExists(filePart.ref))

  end TemporaryFilePart

  object MultipartFormExtractor:

    private val missingKey =
      Results.BadRequest(Json.parse("""{"message":"Could not find key field in request"}"""))

    private val badRedirectUrl =
      Results.BadRequest(Json.parse("""{"message":"Unable to build valid redirect URL for error action"}"""))

    def extractErrorAction(multipartFormData: MultipartFormData[TemporaryFile]): Either[Result, ErrorAction] =
      val maybeErrorActionRedirect = extractErrorActionRedirect(multipartFormData)
      extractKey(multipartFormData)
        .flatMap: key =>
          maybeErrorActionRedirect
            .map: errorActionRedirect =>
              validateErrorActionRedirectUrl(errorActionRedirect, key)
                .map(_ => ErrorAction(maybeErrorActionRedirect, key))
            .getOrElse(Right(ErrorAction(None, key)))

    private def extractErrorActionRedirect(multiPartFormData: MultipartFormData[TemporaryFile]): Option[String] =
      extractSingletonFormValue("error_action_redirect", multiPartFormData)

    private def extractKey(multiPartFormData: MultipartFormData[TemporaryFile]): Either[Result, String] =
      extractSingletonFormValue(KeyName, multiPartFormData).toRight(left = missingKey)

    private def extractSingletonFormValue(
      key              : String,
      multiPartFormData: MultipartFormData[TemporaryFile]
    ): Option[String] =
      multiPartFormData.dataParts
        .get(key)
        .flatMap(_.headOption)

    private def validateErrorActionRedirectUrl(redirectUrl: String, key: String): Either[Result, URI] =
      Try(uri"$redirectUrl".addParam("key", "key").toJavaUri)
        .toOption.toRight(left = badRedirectUrl)

  end MultipartFormExtractor

  object ErrorResponseHandler:

    private val MessageField = "Message"

    def errorResponse(errorAction: ErrorAction, message: String): Result =
      asErrorResult(errorAction, Status.INTERNAL_SERVER_ERROR, Map(fieldName(MessageField) -> message))

    def proxyErrorResponse(
      errorAction    : ErrorAction,
      statusCode     : Int,
      xmlResponseBody: String,
      responseHeaders: Map[String, Seq[String]]
    ): Result =
      asErrorResult(errorAction, statusCode, xmlErrorFields(xmlResponseBody).toMap, responseHeaders)

    private def asErrorResult(
      errorAction    : ErrorAction,
      statusCode     : Int,
      errorFields    : Map[String, String],
      responseHeaders: Map[String, Seq[String]] = Map.empty
    ): Result =
      val resultFields     = errorFields + (KeyName -> errorAction.key)
      val exposableHeaders = responseHeaders.filter { (name, _) => isExposableResponseHeader(name) }

      errorAction.redirectUrl
        .fold(ifEmpty = jsonResult(statusCode, resultFields)): redirectUrl =>
          redirectResult(redirectUrl, queryParams = resultFields)
        .withHeaders(asTuples(exposableHeaders): _*)

    /*
     * This is a dummy placeholder to draw attention to the fact that filtering of error response headers is
     * required in the real implementation.  We currently retain only CORS-related headers and custom Amazon
     * headers.  The implementation in this stub differs.  This stub has a CORS filter (the real implementation
     * does not), and our UploadController does not add any fake Amazon headers - so we actually have nothing
     * to do here ...
     */
    private def isExposableResponseHeader(name: String): Boolean =
      false

    private def jsonResult(statusCode: Int, fields: Map[String, String]): Result =
      Results.Status(statusCode)(Json.toJsObject(fields))

    private def redirectResult(url: String, queryParams: Map[String, String]): Result =
      Results.SeeOther:
        uri"$url".addParams(queryParams).toString

    private def xmlErrorFields(xmlBody: String): Seq[(String, String)] =
      Try(scala.xml.XML.loadString(xmlBody))
        .toOption.toList.flatMap: xml =>
          val requestId = makeOptionalField("RequestId", xml)
          val resource  = makeOptionalField("Resource", xml)
          val message   = makeOptionalField(MessageField, xml)
          val code      = makeOptionalField("Code", xml)
          Seq(code, message, resource, requestId).flatten

    private def makeOptionalField(elemType: String, xml: Elem): Option[(String, String)] =
      (xml \ elemType).headOption.map(node => fieldName(elemType) -> node.text)

    private def fieldName(elemType: String): String =
      s"error$elemType"

  end ErrorResponseHandler

  def asTuples(values: Map[String, Seq[String]]): Seq[(String, String)] =
    values.toList.flatMap { (h, v) => v.map((h, _)) }

end UploadProxyController
