package controllers

import java.io.File
import javax.inject.Inject

import models.{PreparedUpload, UploadSettings}
import play.api.Logger
import play.api.libs.json.{Json, _}
import play.api.mvc.Action
import services.{FileStorageService, PrepareUploadService}
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.Future

class UpscanController @Inject()(prepareUploadService: PrepareUploadService,
                                 storageService: FileStorageService
                                ) extends BaseController {

  def prepareUpload(): Action[JsValue] =
    Action.async(parse.json) { implicit request =>
      withJsonBody[UploadSettings] { (fileUploadDetails: UploadSettings) =>
        Logger.debug(s"Processing request: [$fileUploadDetails].")
        val result: PreparedUpload = prepareUploadService.prepareUpload(fileUploadDetails)
        Future.successful(Ok(Json.toJson(result)))
      }
    }

  def upload() = Action(parse.multipartFormData) { request =>
    request.body.file("file") flatMap { fileField =>
      request.body.dataParts.get("key") map { key =>
        val storeResult: String = storageService.store(fileField.ref, key.head)
        Ok("File uploaded")
      }
    } getOrElse {
      BadRequest(Json.obj("error" -> "Form does not contain valid file field"))
    }
  }
}
