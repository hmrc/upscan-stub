package controllers

import akka.util.ByteString
import javax.inject.Inject
import models.{PreparedUpload, Reference, UploadSettings, UploadValues}
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.http.HttpEntity
import play.api.libs.json.{Json, _}
import play.api.mvc.{Action, RequestHeader, ResponseHeader, Result}
import services.{FileStorageService, NotificationService, PrepareUploadService}
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.{ExecutionContext, Future}
import scala.xml.Node

class UpscanController @Inject()(
  prepareUploadService: PrepareUploadService,
  storageService: FileStorageService,
  notificationService: NotificationService)(implicit ec: ExecutionContext)
    extends BaseController {

  private val uploadForm: Form[UploadValues] = Form(
    mapping(
      "x-amz-algorithm"  -> nonEmptyText,
      "x-amz-credential" -> nonEmptyText,
      "x-amz-date"       -> nonEmptyText,
      "policy"           -> nonEmptyText,
      "x-amz-signature"  -> nonEmptyText,
      "acl"              -> nonEmptyText,
      "key"              -> nonEmptyText
    )(UploadValues.apply)(UploadValues.unapply)
  )

  def prepareUpload(): Action[JsValue] =
    Action.async(parse.json) { implicit request =>
      withJsonBody[UploadSettings] { (fileUploadDetails: UploadSettings) =>
        Logger.debug(s"Processing request: [$fileUploadDetails].")
        val result: PreparedUpload =
          prepareUploadService.prepareUpload(fileUploadDetails, routes.UpscanController.upload().absoluteURL())
        Future.successful(Ok(Json.toJson(result)))
      }
    }

//  val validatedForm: Either[String, UploadValues] = uploadForm.bindFromRequest().fold(
//    formWithErrors => Left(formWithErrors.errors.mkString(", ")),
//    formValues => Right(formValues)
//  )
//
//  validatedForm match {
//    case Right(uploaded) =>
//      request.body.file("file") map { uploadFile =>
//        storageService.store(uploadFile.ref, Reference(uploaded.key))
//        NoContent
//      } getOrElse BadRequest(invalidRequestBody(
//        "IncorrectNumberOfFilesInPostRequest",
//        "POST requires exactly one file upload per request."
//      ))
//    case Left(errors) =>
//      BadRequest(invalidRequestBody("InvalidArgument", errors))
//  }

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

  private def invalidRequestBody(code: String, message: String): Node =
    xml.XML.loadString(s"""<Error>
      <Code>$code</Code>
      <Message>$message</Message>
      <Resource>NoFileReference</Resource>
      <RequestId>${UUID.randomUUID}</RequestId>
    </Error>""")
}
