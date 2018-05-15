package controllers

import java.io.File

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.scalatest.GivenWhenThen
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.{JsArray, JsValue, Json}
import play.api.mvc.MultipartFormData
import play.api.test.Helpers.route
import play.api.test.{FakeHeaders, FakeRequest, Helpers}
import uk.gov.hmrc.play.test.UnitSpec
import utils.Implicits.Base64StringOps

import scala.concurrent.duration._
import scala.xml.{Elem, XML}

class UploadControllerISpec extends UnitSpec with GuiceOneAppPerSuite with GivenWhenThen {

  implicit val actorSystem: ActorSystem        = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val timeout: akka.util.Timeout      = 10.seconds

  "UploadController" should {

    "return NoContent for valid upload request" in {

      Given("a valid POST multipart form request containing a file")
      val testFile = new File("it/resources/text-to-upload.txt")
      val filePart =
        new MultipartFormData.FilePart[TemporaryFile]("file", "text-to-upload.txt", None, new TemporaryFile(testFile))
      val postBodyForm: MultipartFormData[TemporaryFile] = new MultipartFormData[TemporaryFile](
        dataParts = Map(
          "x-amz-algorithm"         -> Seq("some-algorithm"),
          "x-amz-credential"        -> Seq("some-credentials"),
          "x-amz-date"              -> Seq("some-date"),
          "policy"                  -> Seq("{\"policy\":null}".base64encode),
          "x-amz-signature"         -> Seq("some-signature"),
          "acl"                     -> Seq("some-acl"),
          "key"                     -> Seq("file-key"),
          "x-amz-meta-callback-url" -> Seq("http://mylocalservice.com/callback")
        ),
        files    = Seq(filePart),
        badParts = Nil
      )

      val uploadRequest = FakeRequest(Helpers.POST, "/upscan/upload", FakeHeaders(), postBodyForm)

      When("a request is posted to the /upload endpoint")
      implicit val writer = utils.MultipartFormDataWritable.writeable
      val uploadResponse  = route(app, uploadRequest).get

      Then("a NoContent response should be returned")
      status(uploadResponse) shouldBe 204
    }

    "return Bad Request for invalid form upload request" in {

      Given("a invalid POST multipart form request containing a file")
      val testFile = new File("it/resources/text-to-upload.txt")
      val filePart =
        new MultipartFormData.FilePart[TemporaryFile]("file", "text-to-upload.txt", None, new TemporaryFile(testFile))
      val postBodyForm: MultipartFormData[TemporaryFile] = new MultipartFormData[TemporaryFile](
        dataParts = Map(
          "x-amz-algorithm"  -> Seq("some-algorithm"),
          "x-amz-credential" -> Seq("some-credentials"),
          "x-amz-date"       -> Seq("some-date"),
          "policy"           -> Seq("{\"policy\":null}".base64encode),
          "x-amz-signature"  -> Seq("some-signature"),
          "acl"              -> Seq("some-acl"),
          "key"              -> Seq("file-key")
        ),
        files    = Seq(filePart),
        badParts = Nil
      )

      val uploadRequest = FakeRequest(Helpers.POST, "/upscan/upload", FakeHeaders(), postBodyForm)

      When("a request is posted to the /upload endpoint")
      implicit val writer = utils.MultipartFormDataWritable.writeable
      val uploadResponse  = route(app, uploadRequest).get

      Then("a Bad Request response should be returned")
      status(uploadResponse) shouldBe 400

      And("the body should contain XML detailing the error")
      val uploadBody: String    = bodyOf(uploadResponse)
      val uploadBodyAsXml: Elem = xml.XML.loadString(uploadBody)

      (uploadBodyAsXml \\ "Error").nonEmpty      shouldBe true
      (uploadBodyAsXml \\ "Code").head.text      shouldBe "400"
      (uploadBodyAsXml \\ "Message").head.text   shouldBe "FormError(x-amz-meta-callback-url,List(error.required),List())"
      (uploadBodyAsXml \\ "Resource").head.text  shouldBe "NoFileReference"
      (uploadBodyAsXml \\ "RequestId").head.text shouldBe "SomeRequestId"
    }

    "return Bad Request for an upload request containing no file" in {

      Given("a valid POST multipart form request containing NO file")
      val postBodyForm: MultipartFormData[TemporaryFile] = new MultipartFormData[TemporaryFile](
        dataParts = Map(
          "x-amz-algorithm"         -> Seq("some-algorithm"),
          "x-amz-credential"        -> Seq("some-credentials"),
          "x-amz-date"              -> Seq("some-date"),
          "policy"                  -> Seq("{\"policy\":null}".base64encode),
          "x-amz-signature"         -> Seq("some-signature"),
          "acl"                     -> Seq("some-acl"),
          "key"                     -> Seq("file-key"),
          "x-amz-meta-callback-url" -> Seq("http://mylocalservice.com/callback")
        ),
        files    = Nil,
        badParts = Nil
      )

      val uploadRequest = FakeRequest(Helpers.POST, "/upscan/upload", FakeHeaders(), postBodyForm)

      When("a request is posted to the /upload endpoint")
      implicit val writer = utils.MultipartFormDataWritable.writeable
      val uploadResponse  = route(app, uploadRequest).get

      Then("a NoContent response should be returned")
      status(uploadResponse) shouldBe 400

      And("the body should contain XML detailing the error")
      val uploadBody: String    = bodyOf(uploadResponse)
      val uploadBodyAsXml: Elem = xml.XML.loadString(uploadBody)

      (uploadBodyAsXml \\ "Error").nonEmpty      shouldBe true
      (uploadBodyAsXml \\ "Code").head.text      shouldBe "400"
      (uploadBodyAsXml \\ "Message").head.text   shouldBe "'file' field not found"
      (uploadBodyAsXml \\ "Resource").head.text  shouldBe "NoFileReference"
      (uploadBodyAsXml \\ "RequestId").head.text shouldBe "SomeRequestId"
    }

    "return Bad Request when the uploaded file size is smaller than the minimum limit in the supplied policy" in {
      Given("an invalid request containing invalid file size limits in the policy")
      val policy = policyWithContentLengthRange(100, 1000)

      val testFile = new File(getClass.getResource("/text-to-upload.txt").toURI)
      val filePart =
        new MultipartFormData.FilePart[TemporaryFile]("file", "text-to-upload.txt", None, new TemporaryFile(testFile))
      val postBodyForm: MultipartFormData[TemporaryFile] = new MultipartFormData[TemporaryFile](
        dataParts = Map(
          "x-amz-algorithm"         -> Seq("some-algorithm"),
          "x-amz-credential"        -> Seq("some-credentials"),
          "x-amz-date"              -> Seq("some-date"),
          "x-amz-signature"         -> Seq("some-signature"),
          "policy"                  -> Seq(Json.stringify(policy).base64encode),
          "acl"                     -> Seq("some-acl"),
          "key"                     -> Seq("file-key"),
          "x-amz-meta-callback-url" -> Seq("http://mylocalservice.com/callback")
        ),
        files    = Seq(filePart),
        badParts = Nil
      )

      val uploadRequest = FakeRequest(Helpers.POST, "/upscan/upload", FakeHeaders(), postBodyForm)

      When("the request is POSTed to the /upscan/upload")
      implicit val writer = utils.MultipartFormDataWritable.writeable
      val uploadResponse  = route(app, uploadRequest).get

      Then("a Bad Request response should be returned")
      status(uploadResponse) shouldBe 400

      And("the response body should contain the AWS XML error")
      val responseBody      = bodyOf(uploadResponse)
      val responseBodyAsXml = XML.loadString(responseBody)

      (responseBodyAsXml \\ "Error").nonEmpty    shouldBe true
      (responseBodyAsXml \\ "Code").head.text    shouldBe "EntityTooSmall"
      (responseBodyAsXml \\ "Message").head.text shouldBe "Your proposed upload is smaller than the minimum allowed size"
    }

    "return Bad Request when the uploaded file size exceeds the maximum limit in the supplied policy" in {
      Given("an invalid request containing invalid file size limits in the policy")
      val policy = policyWithContentLengthRange(5, 10)

      val testFile = new File(getClass.getResource("/text-to-upload.txt").toURI)
      val filePart =
        new MultipartFormData.FilePart[TemporaryFile]("file", "text-to-upload.txt", None, new TemporaryFile(testFile))
      val postBodyForm: MultipartFormData[TemporaryFile] = new MultipartFormData[TemporaryFile](
        dataParts = Map(
          "x-amz-algorithm"         -> Seq("some-algorithm"),
          "x-amz-credential"        -> Seq("some-credentials"),
          "x-amz-date"              -> Seq("some-date"),
          "x-amz-signature"         -> Seq("some-signature"),
          "policy"                  -> Seq(Json.stringify(policy).base64encode),
          "acl"                     -> Seq("some-acl"),
          "key"                     -> Seq("file-key"),
          "x-amz-meta-callback-url" -> Seq("http://mylocalservice.com/callback")
        ),
        files    = Seq(filePart),
        badParts = Nil
      )

      val uploadRequest = FakeRequest(Helpers.POST, "/upscan/upload", FakeHeaders(), postBodyForm)

      When("the request is POSTed to the /upscan/upload")
      implicit val writer = utils.MultipartFormDataWritable.writeable
      val uploadResponse  = route(app, uploadRequest).get

      Then("a Bad Request response should be returned")
      status(uploadResponse) shouldBe 400

      And("the response body should contain the AWS XML error")
      val responseBody      = bodyOf(uploadResponse)
      val responseBodyAsXml = XML.loadString(responseBody)

      (responseBodyAsXml \\ "Error").nonEmpty    shouldBe true
      (responseBodyAsXml \\ "Code").head.text    shouldBe "EntityTooLarge"
      (responseBodyAsXml \\ "Message").head.text shouldBe "Your proposed upload exceeds the maximum allowed size"
    }
  }

  private def policyWithContentLengthRange(min: Long, max: Long): JsValue =
    Json.obj(
      "conditions" -> JsArray(
        Seq(Json.arr("content-length-range", min, max))
      )
    )
}
