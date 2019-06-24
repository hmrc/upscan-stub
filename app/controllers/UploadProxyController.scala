package controllers

import akka.stream.IOResult
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString
import javax.inject.Inject
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

  private val missingRedirectUrl =
    Results.BadRequest(Json.parse("""{"message":"Could not find error_action_redirect field in request"}"""))

  def upload(): Action[MultipartFormData[TemporaryFile]] = Action.async(parse.multipartFormData) { implicit request =>
    request.body.dataParts
      .get("error_action_redirect")
      .flatMap(_.headOption)
      .fold(Future.successful(missingRedirectUrl)) { redirectUrl =>
        val response = wsClient
          .url(routes.UploadController.upload().absoluteURL())
          .withFollowRedirects(false)
          .post(Source(dataParts(request.body.dataParts) ++ fileParts(request.body.files)))
        response.map {
          case r if r.status >= 200 && r.status < 400 => toResult(r)
          case r                                      => redirect(redirectUrl, r.body)
        }
      }
  }

  private def getErrorParameter(elemType: String, xml: Elem): Option[String] =
    (xml \ elemType).headOption.map(node => s"error$elemType=${node.text}")

  private def errorParamsList(body: String): List[String] =
    Try(scala.xml.XML.loadString(body)).toOption.toList.flatMap { xml =>
      val requestId = getErrorParameter("RequestId", xml)
      val resource  = getErrorParameter("Resource", xml)
      val message   = getErrorParameter("Message", xml)
      val code      = getErrorParameter("Code", xml)

      List(code, message, resource, requestId).flatten
    }

  private def redirect(url: String, body: String): Result = {
    val errors = errorParamsList(body)
    val queryParams = if (errors.nonEmpty) {
      errors.mkString("?", "&", "")
    } else {
      ""
    }

    Results.Redirect(s"$url$queryParams", 303)
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
