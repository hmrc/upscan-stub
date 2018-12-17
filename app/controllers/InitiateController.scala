package controllers

import java.net.URL

import filters.UserAgentFilter
import javax.inject.Inject
import model.UploadSettings
import play.api.Logger
import play.api.data.validation.ValidationError
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json.{JsPath, JsValue, Json, _}
import play.api.mvc.{Action, Result}
import services.PrepareUploadService
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class InitiateController @Inject()(prepareUploadService: PrepareUploadService)(implicit ec: ExecutionContext)
    extends BaseController with UserAgentFilter {

  implicit val uploadSettingsReads: Reads[UploadSettings] = (
    (JsPath \ "callbackUrl").read[String] and
      (JsPath \ "minimumFileSize").readNullable[Int](min(PrepareUploadService.minFileSize)) and
      (JsPath \ "maximumFileSize").readNullable[Int](min(PrepareUploadService.minFileSize) keepAnd max(PrepareUploadService.maxFileSize)) and
      (JsPath \ "expectedContentType").readNullable[String]
    ) (UploadSettings.apply _)
    .filter(ValidationError("Maximum file size must be equal or greater than minimum file size"))(
      settings =>
        settings.minimumFileSize.getOrElse(0) <= settings.maximumFileSize.getOrElse(PrepareUploadService.maxFileSize)
    )

  def prepareUpload(): Action[JsValue] = withUserAgentHeader {
    Action.async(parse.json) { implicit request =>
      withJsonBody[UploadSettings] { (fileUploadDetails: UploadSettings) =>
        withAllowedCallbackProtocol(fileUploadDetails.callbackUrl){
          Logger.debug(s"Received initiate request: [$fileUploadDetails].")
          val result =
            prepareUploadService.prepareUpload(fileUploadDetails, routes.UploadController.upload().absoluteURL, request.headers.get(USER_AGENT))
          Future.successful(Ok(Json.toJson(result)))
        }
      }
    }
  }

  private[controllers] def withAllowedCallbackProtocol[A](callbackUrl: String)
                                                         (block: => Future[Result]): Future[Result]= {

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
