package controllers

import java.nio.file.{Files, Paths}
import java.security.MessageDigest
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
    request.body.file("file").map { file =>
      val fileName = Paths.get(file.filename).getFileName
      val filePath = Paths.get(s"/tmp/picture/$fileName")
      val checksum = calculateChecksum(Files.readAllBytes(filePath))

      Ok(Json.obj())
    } getOrElse {
      Ok(Json.obj())
    }
  }

  private def calculateChecksum(content: Array[Byte]) = MessageDigest.getInstance("MD5")
    .digest(content)
    .map("%02x".format(_))
    .mkString
}
