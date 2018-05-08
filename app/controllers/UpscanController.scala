package controllers

import akka.util.ByteString
import javax.inject.Inject
import models.{PreparedUpload, Reference, UploadSettings}
import play.api.Logger
import play.api.http.HttpEntity
import play.api.libs.json.{Json, _}
import play.api.mvc.{Action, ResponseHeader, Result}
import services.{FileStorageService, PrepareUploadService}
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.Future

class UpscanController @Inject()(prepareUploadService: PrepareUploadService, storageService: FileStorageService)
    extends BaseController {

  def prepareUpload(): Action[JsValue] =
    Action.async(parse.json) { implicit request =>
      withJsonBody[UploadSettings] { (fileUploadDetails: UploadSettings) =>
        Logger.debug(s"Processing request: [$fileUploadDetails].")
        val result: PreparedUpload = prepareUploadService.prepareUpload(fileUploadDetails)
        Future.successful(Ok(Json.toJson(result)))
      }
    }

  def upload() = Action(parse.multipartFormData) { request =>
    val result = for {
      key  <- request.body.dataParts("key").headOption
      file <- request.body.file("file")
    } yield {
      storageService.store(file.ref, Reference(key))
      Ok
    }

    result getOrElse BadRequest(Json.obj("error" -> "Form does not contain valid file field"))
  }

  def download(reference: String) = Action {
    (for {
      source <- storageService.get(Reference(reference))
    } yield {
      Result(
        header = ResponseHeader(200, Map.empty),
        body   = HttpEntity.Strict(ByteString(source.body), None)
      )
      Ok
    }) getOrElse NotFound

  }

}
