import java.io.File

import org.scalatest.GivenWhenThen
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Writeable
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.Json
import play.api.mvc.MultipartFormData
import play.api.test.Helpers.route
import play.api.test.{FakeHeaders, FakeRequest, Helpers}
import uk.gov.hmrc.play.test.UnitSpec
import utils.Implicits.Base64StringOps

class UploadControllerISpec extends UnitSpec with GuiceOneAppPerSuite with GivenWhenThen {
  "UploadController" should {

    "return NoContent for valid upload request" in {

      Given("a POST multipart form request containing a file")
      val testFile = new File("it/text-to-upload.txt")
      val filePart =
        new MultipartFormData.FilePart[TemporaryFile]("file", "text-to-upload.txt", None, new TemporaryFile(testFile))
      val postBodyForm: MultipartFormData[TemporaryFile] = new MultipartFormData[TemporaryFile](
        dataParts = Map(
          "X-Amz-Algorithm"         -> Seq("some-algorithm"),
          "X-Amz-Credential"        -> Seq("some-credentials"),
          "X-Amz-Date"              -> Seq("some-date"),
          "policy"                  -> Seq("{\"policy\":null}".base64encode),
          "X-Amz-Signature"         -> Seq("some-signature"),
          "acl"                     -> Seq("some-acl"),
          "key"                     -> Seq("file-key"),
          "x-amz-meta-callback-url" -> Seq("http://mylocalservice.com/callback")
        ),
        files    = Seq(filePart),
        badParts = Nil
      )

      implicit val writer: Writeable[play.api.mvc.MultipartFormData[play.api.libs.Files.TemporaryFile]] =
        MultipartFormDataWritable.writeable

      val uploadRequest = FakeRequest(Helpers.POST, "/upscan/upload", FakeHeaders(), postBodyForm)

      When("a request is posted to the /upload endpoint")
      val initiateResponse = route(app, uploadRequest).get

      Then("an empty NoContent response should be returned")
      status(initiateResponse) shouldBe 204
    }
  }
}
