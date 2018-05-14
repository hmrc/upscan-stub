package controllers

import javax.inject.Inject
import model.UploadSettings
import play.api.Logger
import play.api.data.validation.ValidationError
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json.{JsPath, JsValue, Json, _}
import play.api.mvc.Action
import services.PrepareUploadService
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.{ExecutionContext, Future}

class InitiateController @Inject()(prepareUploadService: PrepareUploadService)(implicit ec: ExecutionContext)
    extends BaseController {

  implicit val uploadSettingsReads: Reads[UploadSettings] = (
    (JsPath \ "callbackUrl").read[String] and
      (JsPath \ "minimumFileSize").readNullable[Int](min(PrepareUploadService.minFileSize)) and
      (JsPath \ "maximumFileSize").readNullable[Int](min(PrepareUploadService.minFileSize) keepAnd max(PrepareUploadService.maxFileSize)) and
      (JsPath \ "expectedContentType").readNullable[String]
    )(UploadSettings.apply _)
    .filter(ValidationError("Maximum file size must be equal or greater than minimum file size"))(
      settings =>
        settings.minimumFileSize.getOrElse(0) <= settings.maximumFileSize.getOrElse(PrepareUploadService.maxFileSize)
    )

  def prepareUpload(): Action[JsValue] =
    Action.async(parse.json) { implicit request =>
      withJsonBody[UploadSettings] { (fileUploadDetails: UploadSettings) =>
        Logger.debug(s"Processing request: [$fileUploadDetails].")
        val result =
          prepareUploadService.prepareUpload(fileUploadDetails, routes.UploadController.upload().absoluteURL())
        Future.successful(Ok(Json.toJson(result)))
      }
    }
}
