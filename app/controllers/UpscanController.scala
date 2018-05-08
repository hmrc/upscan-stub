package controllers

import java.util.UUID
import javax.inject.Inject

import akka.util.ByteString
import models.{PreparedUpload, Reference, UploadSettings, UploadValues}
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.http.HttpEntity
import play.api.libs.json.{Json, _}
import play.api.mvc.{Action, ResponseHeader, Result}
import services.{FileStorageService, PrepareUploadService}
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.Future
import scala.xml.Node

class UpscanController @Inject()(prepareUploadService: PrepareUploadService, storageService: FileStorageService)
  extends BaseController {

  private val uploadForm: Form[UploadValues] = Form(
    mapping(
      "x-amz-algorithm" -> nonEmptyText,
      "x-amz-credential" -> nonEmptyText,
      "x-amz-date" -> nonEmptyText,
      "policy" -> nonEmptyText,
      "x-amz-signature" -> nonEmptyText,
      "acl" -> nonEmptyText,
      "key" -> nonEmptyText
    )(UploadValues.apply)(UploadValues.unapply)
  )

  def prepareUpload(): Action[JsValue] =
    Action.async(parse.json) { implicit request =>
      withJsonBody[UploadSettings] { (fileUploadDetails: UploadSettings) =>
        Logger.debug(s"Processing request: [$fileUploadDetails].")
        val result: PreparedUpload = prepareUploadService.prepareUpload(fileUploadDetails)
        Future.successful(Ok(Json.toJson(result)))
      }
    }

  def upload() = Action(parse.multipartFormData) { implicit request =>
    val validatedForm: Either[String, UploadValues] = uploadForm.bindFromRequest().fold(
      formWithErrors => Left(formWithErrors.errors.mkString(", ")),
      formValues => Right(formValues)
    )

    validatedForm match {
      case Right(uploaded) =>
        request.body.file("file") map { uploadFile =>
          storageService.store(uploadFile.ref, Reference(uploaded.key))
          NoContent
        } getOrElse BadRequest(invalidRequestBody(
          "IncorrectNumberOfFilesInPostRequest",
          "POST requires exactly one file upload per request."
        ))
      case Left(errors) =>
        BadRequest(invalidRequestBody("InvalidArgument", errors))
    }
  }

  def download(reference: String) = Action {
    (for {
      source <- storageService.get(Reference(reference))
    } yield {
      Result(
        header = ResponseHeader(200, Map.empty),
        body = HttpEntity.Strict(ByteString(source.body), None)
      )
      Ok
    }) getOrElse NotFound

  }


  private def invalidRequestBody(code: String, message: String): Node = {
    xml.XML.loadString(s"""<Error>
      <Code>$code</Code>
      <Message>$message</Message>
      <Resource>NoFileReference</Resource>
      <RequestId>${UUID.randomUUID}</RequestId>
    </Error>""")
  }
}
