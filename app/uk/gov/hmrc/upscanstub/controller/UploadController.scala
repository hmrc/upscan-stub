/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.upscanstub.controller

import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.Constraints.pattern
import play.api.libs.Files
import play.api.libs.Files.TemporaryFile
import play.api.mvc._
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.upscanstub.model._
import uk.gov.hmrc.upscanstub.service._
import uk.gov.hmrc.upscanstub.util.{ApplicativeHelpers, Base64StringUtils}
import uk.gov.hmrc.upscanstub.util.MultipartFormDataSummaries.{summariseDataParts, summariseFileParts}

import java.net.URL
import java.security.MessageDigest
import java.time.Clock
import javax.inject.Inject
import scala.xml.Node

class UploadController @Inject()(
  storageService            : FileStorageService,
  notificationQueueProcessor: NotificationQueueProcessor,
  virusScanner              : VirusScanner,
  clock                     : Clock,
  cc                        : ControllerComponents
) extends BackendController(cc):

  private val logger = Logger(this.getClass)

  private val uploadForm: Form[UploadPostForm] =
    Form(
      mapping(
        "x-amz-algorithm"         -> nonEmptyText.verifying("Invalid algorithm", { "AWS4-HMAC-SHA256" == _ }),
        "x-amz-credential"        -> nonEmptyText,
        "x-amz-date"              -> nonEmptyText.verifying(pattern("^[0-9]{8}T[0-9]{6}Z$".r, "Invalid x-amz-date format")),
        "policy"                  -> nonEmptyText,
        "x-amz-signature"         -> nonEmptyText,
        "acl"                     -> nonEmptyText.verifying("Invalid acl", { "private" == _ }),
        "key"                     -> nonEmptyText,
        "x-amz-meta-callback-url" -> nonEmptyText,
        "success_action_redirect" -> optional(text),
        "error_action_redirect"   -> optional(text)
      )(UploadPostForm.apply)(upf => Some(Tuple.fromProductTyped(upf)))
    )

  lazy val upload: Action[MultipartFormData[TemporaryFile]] =
    Action(parse.multipartFormData): request =>
      given Request[_] = request
      logger.debug(s"Upload form contains dataParts=${summariseDataParts(request.body.dataParts)} and fileParts=${summariseFileParts(request.body.files)}")
      val validatedForm: Either[Seq[String], UploadPostForm] =
        uploadForm
          .bindFromRequest()
          .fold(
            formWithErrors =>
              val errors = formWithErrors.errors.map(_.toString)
              logger.debug(s"Error binding uploaded form: [$errors].")
              Left(errors)
            ,
            formValues =>
              logger.debug(s"Received uploaded form: [$formValues].")
              Right(formValues)
          )

      val validatedFile: Either[Seq[String], MultipartFormData.FilePart[TemporaryFile]] =
        request.body.file("file").map(Right(_)).getOrElse(Left(Seq("'file' field not found")))

      val validatedInput: Either[Iterable[String], (UploadPostForm, MultipartFormData.FilePart[TemporaryFile])] =
        ApplicativeHelpers.product(validatedForm, validatedFile)

      validatedInput.fold(
        errors     => BadRequest(invalidRequestBody("InvalidArgument", errors.mkString(", "))),
        validInput => withPolicyChecked(validInput._1, validInput._2):
                        storeAndNotify(validInput._1, validInput._2)
                        validInput._1.redirectAfterSuccess.fold(NoContent)(SeeOther)
      )

  end upload

  private def withPolicyChecked(
    form: UploadPostForm,
    file: MultipartFormData.FilePart[Files.TemporaryFile]
  )(
    block: => Result
  ): Result =
    val maybeContentLengthCondition: Option[ContentLengthRange] =
      ContentLengthRange.extract(Base64StringUtils.base64decode(form.policy))

    val maybeInvalidContentLength: Option[AWSError] =
      maybeContentLengthCondition match
        case Some(ContentLengthRange(minMaybe, maxMaybe)) =>
          checkFileSizeConstraints(file.ref.path.toFile.length(), minMaybe, maxMaybe)
        case _ =>
          None

    val maybeForcedTestFileError: Option[AWSError] =
      if file.filename.startsWith("reject.") then
        Some:
          AWSError(
            file.filename.drop(7).takeWhile(_ != '.'),
            "we were instructed to reject this upload",
            ""
          )
      else
        None

    maybeForcedTestFileError.orElse(maybeInvalidContentLength) match
      case Some(awsError) => form.redirectAfterError match
                               case Some(url) => Redirect(url, queryParamsFor(form.key, awsError), 303)
                               case None      => BadRequest(invalidRequestBody(awsError.code, awsError.message))
      case None           => block

  private def checkFileSizeConstraints(
    fileSize: Long,
    minMaybe: Option[Long],
    maxMaybe: Option[Long]
  ): Option[AWSError] =
    val minCheck =
      for
        min <- minMaybe
        if fileSize < min
      yield AWSError("EntityTooSmall", "Your proposed upload is smaller than the minimum allowed size", "n/a")

    val maxCheck =
      for
        max <- maxMaybe
        if fileSize > max
      yield AWSError("EntityTooLarge", "Your proposed upload exceeds the maximum allowed size", "n/a")

    minCheck.orElse(maxCheck)

  private def storeAndNotify(
    form: UploadPostForm,
    file: MultipartFormData.FilePart[Files.TemporaryFile]
  )(using
    RequestHeader
  ): Unit =
    val reference  = Reference(form.key)
    val fileId     = storageService.store(file.ref)
    val storedFile = storageService.get(fileId).getOrElse(throw IllegalStateException("The file should have been stored"))

    val fileData = maybeForcedTestFileError(file) match
      case Some(ForcedTestFileError.Quarantine(error)) =>
        ProcessedFile.QuarantinedFile(
          callbackUrl = URL(form.callbackUrl),
          reference   = reference,
          error       = error
        )

      case Some(ForcedTestFileError.Rejected(error)) =>
        ProcessedFile.RejectedFile(
          callbackUrl = URL(form.callbackUrl),
          reference   = reference,
          error       = error
        )

      case Some(ForcedTestFileError.Unknown(error)) =>
        ProcessedFile.UnknownReasonFile(
          callbackUrl = URL(form.callbackUrl),
          reference   = reference,
          error       = error
        )

      case None =>
        val foundVirus: ScanningResult =
          if file.filename.startsWith("infected.") then
            ScanningResult.VirusFound(file.filename.drop(9).takeWhile(_ != '.'))
          else
            virusScanner.checkIfClean(storedFile)

        foundVirus match
          case ScanningResult.Clean =>
            val fileUploadDetails = UploadDetails(
              clock.instant(),
              generateChecksum(storedFile.body),
              mapFilenameToMimeType(filename = file.filename),
              file.filename,
              file.fileSize
            )
            ProcessedFile.UploadedFile(
              callbackUrl   = URL(form.callbackUrl),
              reference     = reference,
              downloadUrl   = URL(buildDownloadUrl(fileId = fileId)),
              uploadDetails = fileUploadDetails
            )
          case ScanningResult.VirusFound(details) =>
            ProcessedFile.QuarantinedFile(
              callbackUrl = URL(form.callbackUrl),
              reference   = reference,
              error       = details
            )

    notificationQueueProcessor.enqueueNotification(fileData)

  private def mapFilenameToMimeType(filename: String): String =
    filename.toLowerCase().substring(filename.indexOf(".") + 1) match
      case "jpg"  => "image/jpeg"
      case "jpeg" => "image/jpeg"
      case "pdf"  => "application/pdf"
      case "png"  => "image/png"
      case "csv"  => "text/csv"
      case "xls"  => "application/vnd.ms-excel"
      case "xlsx" => "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
      case "ods"  => "application/vnd.oasis.opendocument.spreadsheet"
      case _      => s"application/binary"

  private def buildDownloadUrl(fileId: FileId)(using RequestHeader) =
    routes.DownloadController.download(fileId.value).absoluteURL()

  private def invalidRequestBody(code: String, message: String): Node =
    <Error>
      <Code>{code}</Code>
      <Message>{message}</Message>
      <Resource>NoFileReference</Resource>
      <RequestId>SomeRequestId</RequestId>
    </Error>

  def generateChecksum(fileBytes: Array[Byte]): String =
    val checksum = MessageDigest.getInstance("SHA-256").digest(fileBytes)
    checksum.map("%02x" format _).mkString

  private def queryParamsFor(key: String, awsError: AWSError): Map[String, Seq[String]] =
    Map(
      "key"            -> Seq(key),
      "errorCode"      -> Seq(awsError.code),
      "errorMessage"   -> Seq(awsError.message),
      "errorResource"  -> Seq("NoFileReference"),
      "errorRequestId" -> Seq("SomeRequestId")
    )

  private def maybeForcedTestFileError(
    file: MultipartFormData.FilePart[Files.TemporaryFile]
  ): Option[ForcedTestFileError] =
    val name = file.filename
    name.takeWhile(_ != '.') match
      case "infected" => Some(ForcedTestFileError.Quarantine(name.drop(9).takeWhile(_ != '.')))
      case "invalid"  => Some(ForcedTestFileError.Rejected  (name.drop(8).takeWhile(_ != '.')))
      case "unknown"  => Some(ForcedTestFileError.Unknown   (name.drop(8).takeWhile(_ != '.')))
      case _          => None

end UploadController
