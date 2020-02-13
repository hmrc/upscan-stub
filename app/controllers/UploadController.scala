/*
 * Copyright 2020 HM Revenue & Customs
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

package controllers

import java.net.URL
import java.security.MessageDigest
import java.time.Clock

import javax.inject.Inject
import model._
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.Constraints.pattern
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
  virusScanner: VirusScanner,
  clock: Clock)(implicit ec: ExecutionContext)
    extends BaseController {

  private val uploadForm: Form[UploadPostForm] = Form(
    mapping(
      "x-amz-algorithm"         -> nonEmptyText.verifying("Invalid algorithm", { "AWS4-HMAC-SHA256" == _ }),
      "x-amz-credential"        -> nonEmptyText,
      "x-amz-date"              -> nonEmptyText.verifying(pattern("^[0-9]{8}T[0-9]{6}Z$".r, "Invalid x-amz-date format")),
      "policy"                  -> nonEmptyText,
      "x-amz-signature"         -> nonEmptyText,
      "acl"                     -> nonEmptyText.verifying("Invalid acl", { "private" == _ }),
      "key"                     -> nonEmptyText,
      "x-amz-meta-callback-url" -> nonEmptyText,
      "success_action_redirect" -> optional(text)
    )(UploadPostForm.apply)(UploadPostForm.unapply)
  )

  def upload(): Action[MultipartFormData[TemporaryFile]] = Action(parse.multipartFormData) { implicit request =>
    val validatedForm: Either[Seq[String], UploadPostForm] = uploadForm
      .bindFromRequest()
      .fold(
        formWithErrors => {
          val errors = formWithErrors.errors.map(_.toString)
          Logger.debug(s"Error binding uploaded form: [$errors].")
          Left(errors)
        },
        formValues => {
          Logger.debug(s"Received uploaded form: [$formValues].")
          Right(formValues)
        }
      )

    val validatedFile: Either[Seq[String], MultipartFormData.FilePart[TemporaryFile]] =
      request.body.file("file").map(Right(_)).getOrElse(Left(Seq("'file' field not found")))

    val validatedInput: Either[Traversable[String], (UploadPostForm, MultipartFormData.FilePart[TemporaryFile])] =
      ApplicativeHelpers.product(validatedForm, validatedFile)

    validatedInput.fold(
      errors => BadRequest(invalidRequestBody("400", errors.mkString(", "))),
      validInput =>
        withPolicyChecked(validInput._1, validInput._2) {
          storeAndNotify(validInput._1, validInput._2)
          validInput._1.redirectAfterSuccess.fold(NoContent)(SeeOther)
      }
    )
  }

  private def withPolicyChecked(form: UploadPostForm, file: MultipartFormData.FilePart[Files.TemporaryFile])(
    block: => Result): Result = {
    import utils.Implicits.Base64StringOps

    val policyJson: String = form.policy.base64decode()

    val maybeContentLengthCondition: Option[ContentLengthRange] = ContentLengthRange.extract(policyJson)

    val maybeInvalidContentLength: Option[AWSError] =
      maybeContentLengthCondition match {
        case Some(ContentLengthRange(minMaybe, maxMaybe)) =>
          checkFileSizeConstraints(file.ref.file.length(), minMaybe, maxMaybe)
        case _ => None
      }

    maybeInvalidContentLength match {
      case Some(awsError) => BadRequest(invalidRequestBody(awsError.code, awsError.message))
      case None           => block
    }
  }

  private def checkFileSizeConstraints(
    fileSize: Long,
    minMaybe: Option[Long],
    maxMaybe: Option[Long]): Option[AWSError] = {
    val minErrorMaybe: Option[AWSError] = for {
      min <- minMaybe
      if fileSize < min
    } yield AWSError("EntityTooSmall", "Your proposed upload is smaller than the minimum allowed size", "n/a")

    minErrorMaybe orElse {
      for {
        max <- maxMaybe
        if fileSize > max
      } yield AWSError("EntityTooLarge", "Your proposed upload exceeds the maximum allowed size", "n/a")
    }
  }

  private def storeAndNotify(form: UploadPostForm, file: MultipartFormData.FilePart[Files.TemporaryFile])(
    implicit request: RequestHeader): Unit = {
    val reference = Reference(form.key)

    val fileId = storageService.store(file.ref)
    val storedFile =
      storageService.get(fileId).getOrElse(throw new IllegalStateException("The file should have been stored"))

    val foundVirus: ScanningResult = virusScanner.checkIfClean(storedFile)

    val fileData = foundVirus match {
      case Clean => {
        val fileUploadDetails = UploadDetails(
          clock.instant(),
          generateChecksum(storedFile.body),
          mapFilenameToMimeType(filename = file.filename),
          file.filename)
        UploadedFile(
          callbackUrl   = new URL(form.callbackUrl),
          reference     = reference,
          downloadUrl   = new URL(buildDownloadUrl(fileId = fileId)),
          uploadDetails = fileUploadDetails
        )
      }
      case VirusFound(details) =>
        QuarantinedFile(
          callbackUrl = new URL(form.callbackUrl),
          reference   = reference,
          error       = details
        )
    }

    notificationQueueProcessor.enqueueNotification(fileData)

  }

  private def mapFilenameToMimeType(filename: String): String =
    filename.toLowerCase().substring(filename.indexOf(".") + 1) match {
      case "jpg"  => "image/jpeg"
      case "jpeg" => "image/jpeg"
      case "pdf"  => "application/pdf"
      case "png"  => "image/png"
      case _      => s"application/binary"
    }

  private def buildDownloadUrl(fileId: FileId)(implicit request: RequestHeader) =
    routes.DownloadController.download(fileId.value).absoluteURL()

  private def invalidRequestBody(code: String, message: String): Node =
    xml.XML.loadString(s"""<Error>
      <Code>$code</Code>
      <Message>$message</Message>
      <Resource>NoFileReference</Resource>
      <RequestId>SomeRequestId</RequestId>
    </Error>""")

  def generateChecksum(fileBytes: Array[Byte]): String = {
    val checksum = MessageDigest.getInstance("SHA-256").digest(fileBytes)
    checksum.map("%02x" format _).mkString
  }
}
