package controllers

import java.net.URL
import java.time.Instant
import java.util.UUID

import akka.util.ByteString
import javax.inject.Inject
import model.{FileData, Reference, UploadPostForm, UploadSettings}
import models._
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.http.HttpEntity
import play.api.libs.Files
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.{Json, _}
import play.api.mvc._
import services.{FileStorageService, NotificationService, PrepareUploadService}
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import utils.ApplicativeHelpers

import scala.concurrent.{ExecutionContext, Future}
import scala.xml.Node

class UpscanController @Inject()(
  prepareUploadService: PrepareUploadService,
  storageService: FileStorageService,
  notificationService: NotificationService)(implicit ec: ExecutionContext)
    extends BaseController {

  val uploadForm: Form[UploadPostForm] = Form(
    mapping(
      "X-Amz-Algorithm"         -> nonEmptyText,
      "X-Amz-Credential"        -> nonEmptyText,
      "X-Amz-Date"              -> nonEmptyText,
      "policy"                  -> nonEmptyText,
      "X-Amz-Signature"         -> nonEmptyText,
      "acl"                     -> nonEmptyText,
      "key"                     -> nonEmptyText,
      "x-amz-meta-callback-url" -> nonEmptyText
    )(UploadPostForm.apply)(UploadPostForm.unapply)
  )

  def prepareUpload(): Action[JsValue] =
    Action.async(parse.json) { implicit request =>
      withJsonBody[UploadSettings] { (fileUploadDetails: UploadSettings) =>
        Logger.debug(s"Processing request: [$fileUploadDetails].")
        val result =
          prepareUploadService.prepareUpload(fileUploadDetails, routes.UpscanController.upload().absoluteURL())
        Future.successful(Ok(Json.toJson(result)))
      }
    }

  def upload(): Action[MultipartFormData[TemporaryFile]] = Action.async(parse.multipartFormData) { implicit request =>
    val validatedForm = uploadForm
      .bindFromRequest()
      .fold(
        formWithErrors => Left(formWithErrors.errors.map(_.toString)),
        formValues => Right(formValues)
      )

    val validatedFile = request.body.file("file").map(Right(_)).getOrElse(Left(Seq("'file' field not found")))

    val validatedInput = ApplicativeHelpers.product(validatedForm, validatedFile)

    validatedInput.fold(
      errors => Future.successful(BadRequest(invalidRequestBody("400", errors.mkString(", ")))),
      validInput => handleValidUpload(validInput._1, validInput._2)
    )
  }

  private def handleValidUpload(form: UploadPostForm, file: MultipartFormData.FilePart[Files.TemporaryFile])(
    implicit request: RequestHeader) = {

    val reference = Reference(form.key)
    val fileData = FileData(
      callbackUrl     = new URL(form.callbackUrl),
      reference       = reference,
      downloadUrl     = new URL(buildDownloadUrl(reference = reference)),
      size            = file.ref.file.length(),
      uploadTimestamp = Some(Instant.now())
    )
    storageService.store(file.ref, reference)
    notificationService.sendNotification(fileData).map(_ => Ok)
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
