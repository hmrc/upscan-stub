package controllers

import java.net.URL

import javax.inject.Inject
import model._
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
      "x-amz-algorithm"         -> nonEmptyText,
      "x-amz-credential"        -> nonEmptyText,
      "x-amz-date"              -> nonEmptyText,
      "policy"                  -> nonEmptyText,
      "x-amz-signature"         -> nonEmptyText,
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
        formValues     => Right(formValues)
      )

    val validatedFile = request.body.file("file").map(Right(_)).getOrElse(Left(Seq("'file' field not found")))

    val validatedInput = ApplicativeHelpers.product(validatedForm, validatedFile)

    validatedInput.fold(
      errors     => BadRequest(invalidRequestBody("400", errors.mkString(", "))),
      validInput => withPolicyChecked(validInput._1, validInput._2) {
        storeAndNotify(validInput._1, validInput._2)
        NoContent
      }
    )
  }

  private def withPolicyChecked(form: UploadPostForm, file: MultipartFormData.FilePart[Files.TemporaryFile])
                               (block: => Result): Result = {
    import utils.Implicits.Base64StringOps

    val policyJson: String = form.policy.base64decode

    val maybeContentLengthCondition: Option[ContentLengthRange] = ContentLengthRange.extract(policyJson)

    val maybeInvalidContentLength: Option[AWSError] =
      maybeContentLengthCondition match {
        case Some(ContentLengthRange(minMaybe, maxMaybe)) => checkFileSizeConstraints(file.ref.file.length(), minMaybe, maxMaybe)
        case _                                            => None
      }

    maybeInvalidContentLength match {
      case Some(awsError) => BadRequest(invalidRequestBody(awsError.code, awsError.message))
      case None           => block
    }
  }

  private def checkFileSizeConstraints(fileSize: Long, minMaybe: Option[Long], maxMaybe: Option[Long]): Option[AWSError] = {
    val minErrorMaybe: Option[AWSError] = for {
      min <- minMaybe
      if (fileSize < min)
    } yield AWSError("EntityTooSmall", "Your proposed upload is smaller than the minimum allowed size", "n/a")

    minErrorMaybe orElse {
      for {
        max <- maxMaybe
        if (fileSize > max)
      } yield AWSError("EntityTooLarge", "Your proposed upload exceeds the maximum allowed size", "n/a")
    }
  }

  private def storeAndNotify(form: UploadPostForm, file: MultipartFormData.FilePart[Files.TemporaryFile])
                            (implicit request: RequestHeader): Unit = {
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
      <RequestId>SomeRequestId</RequestId>
    </Error>""")
}
