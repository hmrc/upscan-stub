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
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.{JsArray, JsValue, Json}
import play.api.mvc.MultipartFormData
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest, Helpers}
import uk.gov.hmrc.upscanstub.it.util.{CreateTempFileFromResource, MultipartFormDataWritable}
import uk.gov.hmrc.upscanstub.util.Implicits.Base64StringOps

import scala.xml.{Elem, XML}

class UploadControllerISpec
  extends AnyWordSpec
     with Matchers
     with GuiceOneAppPerSuite
     with GivenWhenThen:

  implicit val actorSystem: ActorSystem   = ActorSystem()
  implicit val materializer: Materializer = NoMaterializer

  "UploadController" should:
    "return NoContent for valid upload request without redirect on success" in:
      Given("a valid POST multipart form request containing a file")
      val filePart =
        new MultipartFormData.FilePart[TemporaryFile](
          "file",
          "text-to-upload.txt",
          None,
          CreateTempFileFromResource("/text-to-upload.txt"))
      val postBodyForm: MultipartFormData[TemporaryFile] = new MultipartFormData[TemporaryFile](
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

      val uploadRequest = FakeRequest(Helpers.POST, "/upscan/upload", FakeHeaders(), postBodyForm)

      When("a request is posted to the /upload endpoint")
      implicit val writer = MultipartFormDataWritable.writeable
      val uploadResponse  = route(app, uploadRequest).get

      Then("a NoContent response should be returned")
      status(uploadResponse) shouldBe 204

    "return Redirect for valid upload request with redirect on success" in:
      Given("a valid POST multipart form request containing a file")
      val filePart =
        new MultipartFormData.FilePart[TemporaryFile](
          "file",
          "text-to-upload.txt",
          None,
          CreateTempFileFromResource("/text-to-upload.txt"))
      val postBodyForm: MultipartFormData[TemporaryFile] = new MultipartFormData[TemporaryFile](
        dataParts = Map(
          "x-amz-algorithm"         -> Seq("AWS4-HMAC-SHA256"),
          "x-amz-credential"        -> Seq("some-credentials"),
          "x-amz-date"              -> Seq("20180517T113023Z"),
          "policy"                  -> Seq("{\"policy\":null}".base64encode()),
          "x-amz-signature"         -> Seq("some-signature"),
          "acl"                     -> Seq("private"),
          "key"                     -> Seq("file-key"),
          "success_action_redirect" -> Seq("https://localhost"),
          "x-amz-meta-callback-url" -> Seq("http://mylocalservice.com/callback")
        ),
        files    = Seq(filePart),
        badParts = Nil
      )

      val uploadRequest = FakeRequest(Helpers.POST, "/upscan/upload", FakeHeaders(), postBodyForm)

      When("a request is posted to the /upload endpoint")
      implicit val writer = MultipartFormDataWritable.writeable
      val uploadResponse  = route(app, uploadRequest).get

      Then("a NoContent response should be returned")
      status(uploadResponse)                           shouldBe 303
      await(uploadResponse).header.headers("Location") shouldBe "https://localhost"

    "return Bad Request for invalid form upload request" in:
      Given("a invalid POST multipart form request containing a file")
      val filePart =
        new MultipartFormData.FilePart[TemporaryFile](
          "file",
          "text-to-upload.txt",
          None,
          CreateTempFileFromResource("/text-to-upload.txt"))
      val postBodyForm: MultipartFormData[TemporaryFile] = new MultipartFormData[TemporaryFile](
        dataParts = Map(
          "x-amz-algorithm"  -> Seq("AWS4-HMAC-SHA256"),
          "x-amz-credential" -> Seq("some-credentials"),
          "x-amz-date"       -> Seq("20180517T113023Z"),
          "policy"           -> Seq("{\"policy\":null}".base64encode()),
          "x-amz-signature"  -> Seq("some-signature"),
          "acl"              -> Seq("private"),
          "key"              -> Seq("file-key")
        ),
        files    = Seq(filePart),
        badParts = Nil
      )

      val uploadRequest = FakeRequest(Helpers.POST, "/upscan/upload", FakeHeaders(), postBodyForm)

      When("a request is posted to the /upload endpoint")
      implicit val writer = MultipartFormDataWritable.writeable
      val uploadResponse  = route(app, uploadRequest).get

      Then("a Bad Request response should be returned")
      status(uploadResponse) shouldBe 400

      And("the body should contain XML detailing the error")
      val uploadBody: String    = contentAsString(uploadResponse)
      val uploadBodyAsXml: Elem = xml.XML.loadString(uploadBody)

      (uploadBodyAsXml \\ "Error").nonEmpty      shouldBe true
      (uploadBodyAsXml \\ "Code").head.text      shouldBe "InvalidArgument"
      (uploadBodyAsXml \\ "Message").head.text   shouldBe "FormError(x-amz-meta-callback-url,List(error.required),List())"
      (uploadBodyAsXml \\ "Resource").head.text  shouldBe "NoFileReference"
      (uploadBodyAsXml \\ "RequestId").head.text shouldBe "SomeRequestId"

    "return Bad Request for an upload request containing no file" in:
      Given("a valid POST multipart form request containing NO file")
      val postBodyForm: MultipartFormData[TemporaryFile] = new MultipartFormData[TemporaryFile](
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

      val uploadRequest = FakeRequest(Helpers.POST, "/upscan/upload", FakeHeaders(), postBodyForm)

      When("a request is posted to the /upload endpoint")
      implicit val writer = MultipartFormDataWritable.writeable
      val uploadResponse  = route(app, uploadRequest).get

      Then("a NoContent response should be returned")
      status(uploadResponse) shouldBe 400

      And("the body should contain XML detailing the error")
      val uploadBody: String    = contentAsString(uploadResponse)
      val uploadBodyAsXml: Elem = xml.XML.loadString(uploadBody)

      (uploadBodyAsXml \\ "Error").nonEmpty      shouldBe true
      (uploadBodyAsXml \\ "Code").head.text      shouldBe "InvalidArgument"
      (uploadBodyAsXml \\ "Message").head.text   shouldBe "'file' field not found"
      (uploadBodyAsXml \\ "Resource").head.text  shouldBe "NoFileReference"
      (uploadBodyAsXml \\ "RequestId").head.text shouldBe "SomeRequestId"

    "return Bad Request when the uploaded file size is smaller than the minimum limit in the supplied policy" in:
      Given("an invalid request containing invalid file size limits in the policy")
      val policy = policyWithContentLengthRange(100, 1000)

      val filePart =
        new MultipartFormData.FilePart[TemporaryFile](
          "file",
          "text-to-upload.txt",
          None,
          CreateTempFileFromResource("/text-to-upload.txt"))
      val postBodyForm: MultipartFormData[TemporaryFile] = new MultipartFormData[TemporaryFile](
        dataParts = Map(
          "x-amz-algorithm"         -> Seq("AWS4-HMAC-SHA256"),
          "x-amz-credential"        -> Seq("some-credentials"),
          "x-amz-date"              -> Seq("20180517T113023Z"),
          "x-amz-signature"         -> Seq("some-signature"),
          "policy"                  -> Seq(Json.stringify(policy).base64encode()),
          "acl"                     -> Seq("private"),
          "key"                     -> Seq("file-key"),
          "x-amz-meta-callback-url" -> Seq("http://mylocalservice.com/callback")
        ),
        files    = Seq(filePart),
        badParts = Nil
      )

      val uploadRequest = FakeRequest(Helpers.POST, "/upscan/upload", FakeHeaders(), postBodyForm)

      When("the request is POSTed to the /upscan/upload")
      implicit val writer = MultipartFormDataWritable.writeable
      val uploadResponse  = route(app, uploadRequest).get

      Then("a Bad Request response should be returned")
      status(uploadResponse) shouldBe 400

      And("the response body should contain the AWS XML error")
      val responseBody      = contentAsString(uploadResponse)
      val responseBodyAsXml = XML.loadString(responseBody)

      (responseBodyAsXml \\ "Error").nonEmpty    shouldBe true
      (responseBodyAsXml \\ "Code").head.text    shouldBe "EntityTooSmall"
      (responseBodyAsXml \\ "Message").head.text shouldBe "Your proposed upload is smaller than the minimum allowed size"

    "return Bad Request when the uploaded file size exceeds the maximum limit in the supplied policy" in:
      Given("an invalid request containing invalid file size limits in the policy")
      val policy = policyWithContentLengthRange(5, 10)

      val filePart =
        new MultipartFormData.FilePart[TemporaryFile](
          "file",
          "text-to-upload.txt",
          None,
          CreateTempFileFromResource("/text-to-upload.txt"))
      val postBodyForm: MultipartFormData[TemporaryFile] = new MultipartFormData[TemporaryFile](
        dataParts = Map(
          "x-amz-algorithm"         -> Seq("AWS4-HMAC-SHA256"),
          "x-amz-credential"        -> Seq("some-credentials"),
          "x-amz-date"              -> Seq("20180517T113023Z"),
          "x-amz-signature"         -> Seq("some-signature"),
          "policy"                  -> Seq(Json.stringify(policy).base64encode()),
          "acl"                     -> Seq("private"),
          "key"                     -> Seq("file-key"),
          "x-amz-meta-callback-url" -> Seq("http://mylocalservice.com/callback")
        ),
        files    = Seq(filePart),
        badParts = Nil
      )

      val uploadRequest = FakeRequest(Helpers.POST, "/upscan/upload", FakeHeaders(), postBodyForm)

      When("the request is POSTed to the /upscan/upload")
      implicit val writer = MultipartFormDataWritable.writeable
      val uploadResponse  = route(app, uploadRequest).get

      Then("a Bad Request response should be returned")
      status(uploadResponse) shouldBe 400

      And("the response body should contain the AWS XML error")
      val responseBody      = contentAsString(uploadResponse)
      val responseBodyAsXml = XML.loadString(responseBody)

      (responseBodyAsXml \\ "Error").nonEmpty    shouldBe true
      (responseBodyAsXml \\ "Code").head.text    shouldBe "EntityTooLarge"
      (responseBodyAsXml \\ "Message").head.text shouldBe "Your proposed upload exceeds the maximum allowed size"

  private def policyWithContentLengthRange(min: Long, max: Long): JsValue =
    Json.obj(
      "conditions" -> JsArray(
        Seq(Json.arr("content-length-range", min, max))
      )
    )
