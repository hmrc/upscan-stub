package controllers

import java.io.File
import javax.inject.Inject

import models.{PreparedUpload, UploadSettings}
import play.api.Logger
import play.api.libs.json.{Json, _}
import play.api.mvc.Action
import services.PrepareUploadService
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.Future

class UpscanController @Inject()(prepareUploadService: PrepareUploadService) extends BaseController {

  def prepareUpload(): Action[JsValue] =
    Action.async(parse.json) { implicit request =>
      withJsonBody[UploadSettings] { (fileUploadDetails: UploadSettings) =>
        Logger.debug(s"Processing request: [$fileUploadDetails].")
        val result: PreparedUpload = prepareUploadService.prepareUpload(fileUploadDetails)
        Future.successful(Ok(Json.toJson(result)))
      }
    }

  def upload() = Action(parse.multipartFormData) { request =>
    request.body.file("file").map { fileField =>
      // TODO: Validate form and fake AWS response

      val filename = fileField.filename
      // TODO: Put this in a tmp directory created on startup

      fileField.ref.moveTo(new File(s"/resources/uploaded/$filename"))
      Ok("File uploaded")
    } getOrElse {
      BadRequest(Json.obj("error" -> "Form does not contain valid file field"))
    }
  }
}
