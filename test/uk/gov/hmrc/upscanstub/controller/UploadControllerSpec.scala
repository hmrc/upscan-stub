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

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.testkit.NoMaterializer
import org.mockito.ArgumentMatchers.any
import org.mockito.{Mockito, MockitoSugar}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.Files.TemporaryFile
import play.api.mvc.{MultipartFormData, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.upscanstub.model._
import uk.gov.hmrc.upscanstub.service._
import uk.gov.hmrc.upscanstub.test.util.CreateTempFileFromResource
import uk.gov.hmrc.upscanstub.util.Implicits.Base64StringOps

import java.net.URL
import java.time.{Clock, Instant, ZoneId}
import scala.concurrent.Future
import scala.xml.Elem

class UploadControllerSpec extends AnyWordSpec with Matchers with GivenWhenThen with MockitoSugar {

  implicit val actorSystem: ActorSystem        = ActorSystem()
  implicit val materializer: Materializer      = NoMaterializer

  private val initiateDate = Instant.parse("2018-04-24T09:30:00Z")
  private val testClock    = new TestClock(initiateDate)

  "UploadController" should {
    "upload a successfully POSTed form and file" in {

      Given("a valid form containing a valid file")
      val fileToUpload = CreateTempFileFromResource("/text-to-upload.txt")
      val filePart = new MultipartFormData.FilePart[TemporaryFile](
        key = "file",
        filename = "text-to-upload.pdf",
        contentType = None,
        fileToUpload,
        fileSize = fileToUpload.length()
      )
      val formDataBody = new MultipartFormData[TemporaryFile](
        dataParts = Map(
          "x-amz-algorithm"         -> Seq("AWS4-HMAC-SHA256"),
          "x-amz-credential"        -> Seq("some-credentials"),
          "x-amz-date"              -> Seq("20180517T113023Z"),
          "policy"                  -> Seq("{\"policy\":null}".base64encode()),
          "x-amz-signature"         -> Seq("some-signature"),
          "acl"                     -> Seq("private"),
          "key"                     -> Seq("file-key"),
          "x-amz-meta-callback-url" -> Seq("http://mylocalservice.com/callback")
        ),
        files    = Seq(filePart),
        badParts = Nil
      )
      val uploadRequest = FakeRequest().withBody(formDataBody)

      val storedFile     = StoredFile(Array())
      val storageService = mock[FileStorageService]
      val fileId         = FileId("testFileId")
      Mockito.when(storageService.store(any())).thenReturn(fileId)
      Mockito.when(storageService.get(fileId)).thenReturn(Some(storedFile))

      val notificationProcessor = mock[NotificationQueueProcessor]
      val virusScanner          = mock[VirusScanner]
      Mockito.when(virusScanner.checkIfClean(any())).thenReturn(Clean)

      val controller =
        new UploadController(storageService, notificationProcessor, virusScanner, testClock, stubControllerComponents())

      When("upload is called")
      val uploadResult: Future[Result] = controller.upload()(uploadRequest)

      Then("the file should be saved to storage service")
      Mockito.verify(storageService).store(any())

      And("the notification service should be called")
      Mockito
        .verify(notificationProcessor)
        .enqueueNotification(UploadedFile(
          new URL("http://mylocalservice.com/callback"),
          Reference("file-key"),
          new URL(s"http://localhost/download/${fileId.value}"),
          UploadDetails(
            uploadTimestamp = initiateDate,
            checksum = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            fileMimeType = "application/pdf",
            fileName = "text-to-upload.pdf",
            size = fileToUpload.length()
          )
        ))

      And("a No Content response should be returned")
      val uploadStatus = status(uploadResult)
      uploadStatus shouldBe 204
    }

    "return HTTP redirect when redirect after success requested" in {
      Given("a valid form containing a valid file")
      val fileToUpload = CreateTempFileFromResource("/text-to-upload.txt")
      val filePart = new MultipartFormData.FilePart[TemporaryFile](
        key = "file",
        filename = "text-to-upload.pdf",
        contentType = None,
        fileToUpload,
        fileSize = fileToUpload.length()
      )
      val formDataBody = new MultipartFormData[TemporaryFile](
        dataParts = Map(
          "x-amz-algorithm"         -> Seq("AWS4-HMAC-SHA256"),
          "x-amz-credential"        -> Seq("some-credentials"),
          "x-amz-date"              -> Seq("20180517T113023Z"),
          "policy"                  -> Seq("{\"policy\":null}".base64encode()),
          "x-amz-signature"         -> Seq("some-signature"),
          "acl"                     -> Seq("private"),
          "key"                     -> Seq("file-key"),
          "x-amz-meta-callback-url" -> Seq("http://mylocalservice.com/callback"),
          "success_action_redirect" -> Seq("https://localhost")
        ),
        files    = Seq(filePart),
        badParts = Nil
      )
      val uploadRequest = FakeRequest().withBody(formDataBody)

      val storedFile     = StoredFile(Array())
      val storageService = mock[FileStorageService]
      val fileId         = FileId("testFileId")
      Mockito.when(storageService.store(any())).thenReturn(fileId)
      Mockito.when(storageService.get(fileId)).thenReturn(Some(storedFile))

      val notificationProcessor = mock[NotificationQueueProcessor]
      val virusScanner          = mock[VirusScanner]
      Mockito.when(virusScanner.checkIfClean(any())).thenReturn(Clean)

      val controller =
        new UploadController(storageService, notificationProcessor, virusScanner, testClock, stubControllerComponents())

      When("upload is called")
      val uploadResult: Future[Result] = controller.upload()(uploadRequest)

      Then("the file should be saved to storage service")
      Mockito.verify(storageService).store(any())

      And("the notification service should be called")
      Mockito
        .verify(notificationProcessor)
        .enqueueNotification(UploadedFile(
          new URL("http://mylocalservice.com/callback"),
          Reference("file-key"),
          new URL(s"http://localhost/download/${fileId.value}"),
          UploadDetails(
            uploadTimestamp = initiateDate,
            checksum = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            fileMimeType = "application/pdf",
            fileName = "text-to-upload.pdf",
            size = fileToUpload.length()
          )
        ))

      And("a See Other response should be returned")
      val uploadStatus = status(uploadResult)
      uploadStatus                                   shouldBe 303
      await(uploadResult).header.headers("Location") shouldBe "https://localhost"

    }

    "store details of a file that fails virus scanning and return successful" in {

      Given("a valid form containing a valid file")
      val filePart =
        new MultipartFormData.FilePart[TemporaryFile](
          "file",
          "text-to-upload.txt",
          None,
          CreateTempFileFromResource("/text-to-upload.txt"))
      val formDataBody: MultipartFormData[TemporaryFile] = new MultipartFormData[TemporaryFile](
        dataParts = Map(
          "x-amz-algorithm"         -> Seq("AWS4-HMAC-SHA256"),
          "x-amz-credential"        -> Seq("some-credentials"),
          "x-amz-date"              -> Seq("20180517T113023Z"),
          "policy"                  -> Seq("{\"policy\":null}".base64encode()),
          "x-amz-signature"         -> Seq("some-signature"),
          "acl"                     -> Seq("private"),
          "key"                     -> Seq("file-key"),
          "x-amz-meta-callback-url" -> Seq("http://mylocalservice.com/callback")
        ),
        files    = Seq(filePart),
        badParts = Nil
      )
      val uploadRequest = FakeRequest().withBody(formDataBody)

      val storedFile     = StoredFile(Array())
      val storageService = mock[FileStorageService]
      Mockito.when(storageService.get(any())).thenReturn(Some(storedFile))

      val notificationProcessor = mock[NotificationQueueProcessor]
      val virusScanner          = mock[VirusScanner]
      Mockito.when(virusScanner.checkIfClean(any())).thenReturn(VirusFound("This test file failed scanning"))

      val controller =
        new UploadController(storageService, notificationProcessor, virusScanner, testClock, stubControllerComponents())

      When("upload is called")
      val uploadResult: Future[Result] = controller.upload()(uploadRequest)

      Then("the file should be saved to storage service")
      Mockito.verify(storageService).store(any())

      And("the notification service should be called")
      Mockito
        .verify(notificationProcessor)
        .enqueueNotification(
          QuarantinedFile(
            new URL("http://mylocalservice.com/callback"),
            Reference("file-key"),
            "This test file failed scanning"))

      And("a No Content response should be returned")
      val uploadStatus = status(uploadResult)
      uploadStatus shouldBe 204
    }

    "error on an incomplete POSTed form" in {

      Given("an invalid form containing a valid file")
      val filePart =
        new MultipartFormData.FilePart[TemporaryFile](
          "file",
          "text-to-upload.txt",
          None,
          CreateTempFileFromResource("/text-to-upload.txt"))
      val formDataBody: MultipartFormData[TemporaryFile] = new MultipartFormData[TemporaryFile](
        dataParts = Map(
          "x-amz-algorithm"         -> Seq("AWS4-HMAC-SHA256"),
          "x-amz-credential"        -> Seq("some-credentials"),
          "x-amz-date"              -> Seq("20180517T113023Z"),
          "x-amz-signature"         -> Seq("some-signature"),
          "x-amz-meta-callback-url" -> Seq("http://mylocalservice.com/callback")
        ),
        files    = Seq(filePart),
        badParts = Nil
      )
      val uploadRequest = FakeRequest().withBody(formDataBody)

      val storageService        = mock[FileStorageService]
      val notificationProcessor = mock[NotificationQueueProcessor]
      val virusScanner          = mock[VirusScanner]

      val controller =
        new UploadController(storageService, notificationProcessor, virusScanner, testClock, stubControllerComponents())

      When("upload is called")
      val uploadResult: Future[Result] = controller.upload()(uploadRequest)

      Then("a Bad Request response should be returned")
      val uploadStatus = status(uploadResult)
      uploadStatus shouldBe 400

      And("the body should contain XML detailing the error")
      val uploadBody: String    = contentAsString(uploadResult)
      val uploadBodyAsXml: Elem = xml.XML.loadString(uploadBody)

      (uploadBodyAsXml \\ "Error").nonEmpty      shouldBe true
      (uploadBodyAsXml \\ "Code").head.text      shouldBe "InvalidArgument"
      (uploadBodyAsXml \\ "Message").head.text   shouldBe "FormError(policy,List(error.required),List()), FormError(acl,List(error.required),List()), FormError(key,List(error.required),List())"
      (uploadBodyAsXml \\ "Resource").head.text  shouldBe "NoFileReference"
      (uploadBodyAsXml \\ "RequestId").head.text shouldBe "SomeRequestId"
    }

    "redirect on a forced rejected upload" in {

      Given("a valid form containing a valid file with forced error name schema")
      val filePart =
        new MultipartFormData.FilePart[TemporaryFile](
          "file",
          "reject.UnexpectedContent.txt",
          None,
          CreateTempFileFromResource("/text-to-upload.txt"))
      val formDataBody: MultipartFormData[TemporaryFile] = new MultipartFormData[TemporaryFile](
        dataParts = Map(
          "x-amz-algorithm"         -> Seq("AWS4-HMAC-SHA256"),
          "x-amz-credential"        -> Seq("some-credentials"),
          "x-amz-date"              -> Seq("20180517T113023Z"),
          "policy"                  -> Seq("{\"policy\":null}".base64encode()),
          "x-amz-signature"         -> Seq("some-signature"),
          "acl"                     -> Seq("private"),
          "key"                     -> Seq("file-key"),
          "x-amz-meta-callback-url" -> Seq("http://mylocalservice.com/callback"),
          "error_action_redirect" -> Seq("https://localhost")
        ),
        files    = Seq(filePart),
        badParts = Nil
      )
      val uploadRequest = FakeRequest().withBody(formDataBody)

      val storageService        = mock[FileStorageService]
      val notificationProcessor = mock[NotificationQueueProcessor]
      val virusScanner          = mock[VirusScanner]

      val controller =
        new UploadController(storageService, notificationProcessor, virusScanner, testClock, stubControllerComponents())

      When("upload is called")
      val uploadResult: Future[Result] = controller.upload()(uploadRequest)

      Then("a Redirect response should be returned")
      val uploadStatus = status(uploadResult)
      uploadStatus shouldBe 303

      And("the redirect url should contain error details")
      val redirectUrl = redirectLocation(uploadResult).getOrElse("")
      redirectUrl should include("key=file-key")
      redirectUrl should include("errorCode=UnexpectedContent")
      redirectUrl should include("errorMessage=")
    }

    "error when no file in POSTed form" in {

      Given("a valid form containing a NO file")
      val formDataBody: MultipartFormData[TemporaryFile] = new MultipartFormData[TemporaryFile](
        dataParts = Map(
          "x-amz-algorithm"         -> Seq("AWS4-HMAC-SHA256"),
          "x-amz-credential"        -> Seq("some-credentials"),
          "x-amz-date"              -> Seq("20180517T113023Z"),
          "policy"                  -> Seq("{\"policy\":null}".base64encode()),
          "x-amz-signature"         -> Seq("some-signature"),
          "acl"                     -> Seq("private"),
          "key"                     -> Seq("file-key"),
          "x-amz-meta-callback-url" -> Seq("http://mylocalservice.com/callback")
        ),
        files    = Nil,
        badParts = Nil
      )
      val uploadRequest = FakeRequest().withBody(formDataBody)

      val storageService        = mock[FileStorageService]
      val notificationProcessor = mock[NotificationQueueProcessor]
      val virusScanner          = mock[VirusScanner]

      val controller =
        new UploadController(storageService, notificationProcessor, virusScanner, testClock, stubControllerComponents())

      When("upload is called")
      val uploadResult: Future[Result] = controller.upload()(uploadRequest)

      Then("a Bad Request response should be returned")
      val uploadStatus = status(uploadResult)
      uploadStatus shouldBe 400

      And("the body should contain XML detailing the error")
      val uploadBody: String    = contentAsString(uploadResult)
      val uploadBodyAsXml: Elem = xml.XML.loadString(uploadBody)

      (uploadBodyAsXml \\ "Error").nonEmpty      shouldBe true
      (uploadBodyAsXml \\ "Code").head.text      shouldBe "InvalidArgument"
      (uploadBodyAsXml \\ "Message").head.text   shouldBe "'file' field not found"
      (uploadBodyAsXml \\ "Resource").head.text  shouldBe "NoFileReference"
      (uploadBodyAsXml \\ "RequestId").head.text shouldBe "SomeRequestId"
    }
  }

  class TestClock(fixedInstant: Instant) extends Clock {
    override def instant(): Instant = fixedInstant

    override def withZone(zone: ZoneId): Clock = ???

    override def getZone: ZoneId = ???
  }
}
