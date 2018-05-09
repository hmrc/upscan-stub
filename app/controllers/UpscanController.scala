package controllers

import java.net.URL
import java.time.Instant

import akka.util.ByteString
import javax.inject.Inject
import models.{PreparedUpload, Reference, UploadSettings, UploadedFile}
import play.api.Logger
import play.api.http.HttpEntity
import play.api.libs.json.{Json, _}
import play.api.mvc.{Action, RequestHeader, ResponseHeader, Result}
import services.{FileStorageService, NotificationService, PrepareUploadService}
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.{ExecutionContext, Future}

class UpscanController @Inject()(
  prepareUploadService: PrepareUploadService,
  storageService: FileStorageService,
  notificationService: NotificationService)(implicit ec: ExecutionContext)
    extends BaseController {

  def prepareUpload(): Action[JsValue] =
    Action.async(parse.json) { implicit request =>
      withJsonBody[UploadSettings] { (fileUploadDetails: UploadSettings) =>
        Logger.debug(s"Processing request: [$fileUploadDetails].")
        val result: PreparedUpload =
          prepareUploadService.prepareUpload(fileUploadDetails, routes.UpscanController.upload().absoluteURL())
        Future.successful(Ok(Json.toJson(result)))
      }
    }

  def upload() = Action.async(parse.multipartFormData) { implicit request =>
    val result = for {
      key <- request.body.dataParts("key").headOption
      reference = Reference(key)
      file        <- request.body.file("file")
      callbackUrl <- request.body.dataParts("x-amz-meta-callback-url").headOption
    } yield {

      val uploadedFile = UploadedFile(
        callbackUrl     = new URL(callbackUrl),
        reference       = reference,
        downloadUrl     = new URL(buildDownloadUrl(reference = reference)),
        size            = file.ref.file.length(),
        uploadTimestamp = Some(Instant.now())
      )

      storageService.store(file.ref, reference)
      notificationService.sendNotification(uploadedFile).map(_ => Ok)
    }

    result getOrElse Future.successful(
      BadRequest(
        Json.obj("error" -> ("Form does not contain valid file field. Existing fields" + request.body.dataParts.keys))))
  }

  private def buildDownloadUrl(reference: Reference)(implicit request: RequestHeader) =
    routes.UpscanController.download(reference.value).absoluteURL()

  def download(reference: String) = Action {
    (for {
      source <- storageService.get(Reference(reference))
    } yield {
      Result(
        header = ResponseHeader(200, Map.empty),
        body   = HttpEntity.Strict(ByteString(source.body), None)
      )
    }) getOrElse NotFound

  }

}
