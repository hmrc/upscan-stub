package controllers

import java.net.URL
import java.util.UUID

import javax.inject.Inject
import model.{QuarantinedFile, Reference, UploadPostForm, UploadedFile}
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.Files
import play.api.libs.Files.TemporaryFile
import play.api.mvc._
import services._
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import utils.ApplicativeHelpers

import scala.concurrent.ExecutionContext
import scala.xml.Node

class UploadController @Inject()(
  storageService: FileStorageService,
  notificationQueueProcessor: NotificationQueueProcessor,
  virusScanner: VirusScanner)(implicit ec: ExecutionContext)
    extends BaseController {

  private val uploadForm: Form[UploadPostForm] = Form(
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

  def upload(): Action[MultipartFormData[TemporaryFile]] = Action(parse.multipartFormData) { implicit request =>
    val validatedForm = uploadForm
      .bindFromRequest()
      .fold(
        formWithErrors => Left(formWithErrors.errors.map(_.toString)),
        formValues => Right(formValues)
      )

    val validatedFile = request.body.file("file").map(Right(_)).getOrElse(Left(Seq("'file' field not found")))

    val validatedInput = ApplicativeHelpers.product(validatedForm, validatedFile)

    validatedInput.fold(
      errors => BadRequest(invalidRequestBody("400", errors.mkString(", "))),
      validInput => { handleValidUpload(validInput._1, validInput._2); Ok }
    )
  }

  private def handleValidUpload(form: UploadPostForm, file: MultipartFormData.FilePart[Files.TemporaryFile])(
    implicit request: RequestHeader): Unit = {

    val reference = Reference(form.key)

    storageService.store(file.ref, reference)

    val foundVirus: ScanningResult = virusScanner.checkIfClean(
      storageService.get(reference).getOrElse(throw new IllegalStateException("The file should have been stored")))

    val fileData = foundVirus match {
      case Clean =>
        UploadedFile(
          callbackUrl = new URL(form.callbackUrl),
          reference   = reference,
          downloadUrl = new URL(buildDownloadUrl(reference = reference)))
      case VirusFound(details) =>
        QuarantinedFile(
          callbackUrl = new URL(form.callbackUrl),
          reference   = reference,
          error       = details
        )
    }

    notificationQueueProcessor.enqueueNotification(fileData)
  }

  private def buildDownloadUrl(reference: Reference)(implicit request: RequestHeader) =
    routes.DownloadController.download(reference.value).absoluteURL()

  private def invalidRequestBody(code: String, message: String): Node =
    xml.XML.loadString(s"""<Error>
      <Code>$code</Code>
      <Message>$message</Message>
      <Resource>NoFileReference</Resource>
      <RequestId>${UUID.randomUUID}</RequestId>
    </Error>""")
}
