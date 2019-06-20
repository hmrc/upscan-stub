package controllers

import java.net.URL

import filters.UserAgentFilter
import javax.inject.Inject
import model.initiate._
import play.api.Logger
import play.api.libs.json.{JsValue, Json, _}
import play.api.mvc.{Action, Result}
import services.PrepareUploadService
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class InitiateController @Inject()(prepareUploadService: PrepareUploadService)(implicit ec: ExecutionContext)
    extends BaseController
    with UserAgentFilter {

  implicit val prepareUploadRequestV1Reads: Reads[PrepareUploadRequestV1] =
    PrepareUploadRequestV1.reads(PrepareUploadService.maxFileSize)

  implicit val prepareUploadRequestV2Reads: Reads[PrepareUploadRequestV2] =
    PrepareUploadRequestV2.reads(PrepareUploadService.maxFileSize)

  def prepareUploadV1(): Action[JsValue] = prepareUpload[PrepareUploadRequestV1]()

  def prepareUploadV2(): Action[JsValue] = prepareUpload[PrepareUploadRequestV2]()

  private def prepareUpload[T <: PrepareUpload]()(implicit reads: Reads[T], manifest: Manifest[T]): Action[JsValue] =
    withUserAgentHeader {
      Action.async(parse.json) { implicit request =>
        withJsonBody[T] { prepareUpload: T =>
          withAllowedCallbackProtocol(prepareUpload.callbackUrl) {
            Logger.debug(s"Received initiate request: [$prepareUpload].")
            val url = prepareUpload match {
              case _: PrepareUploadRequestV1 => routes.UploadController.upload().absoluteURL
              case _: PrepareUploadRequestV2 => routes.UploadProxyController.upload().absoluteURL
            }
            val result =
              prepareUploadService.prepareUpload(prepareUpload.toUploadSettings(url), request.headers.get(USER_AGENT))
            Future.successful(Ok(Json.toJson(result)(PrepareUploadResponse.format)))
          }
        }
      }
    }

  private[controllers] def withAllowedCallbackProtocol[A](callbackUrl: String)(
    block: => Future[Result]): Future[Result] = {

    val isHttps: Try[Boolean] = Try {
      val url = new URL(callbackUrl)
      url.getProtocol == "https" || callbackUrl.startsWith("http://localhost")
    }

    isHttps match {
      case Success(true) => block
      case Success(false) => {
        Logger.warn(s"Invalid callback url protocol: [$callbackUrl].")

        Future.successful(BadRequest(s"Invalid callback url protocol: [$callbackUrl]. Protocol must be https."))
      }
      case Failure(e) => {
        Logger.warn(s"Invalid callback url format: [$callbackUrl].")

        Future.successful(BadRequest(s"Invalid callback url format: [$callbackUrl]. [${e.getMessage}]"))
      }
    }

  }
}
