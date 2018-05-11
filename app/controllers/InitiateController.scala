package controllers

import javax.inject.Inject

import model.UploadSettings
import play.api.Logger
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Action
import services.PrepareUploadService
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.{ExecutionContext, Future}

class InitiateController @Inject()(prepareUploadService: PrepareUploadService)(implicit ec: ExecutionContext)
    extends BaseController {

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
