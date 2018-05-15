package services

import java.io.File
import java.nio.file.{Files, Paths}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.github.tomakehurst.wiremock.client.WireMock._
import model.PreparedUpload
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, GivenWhenThen}
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.Json
import play.api.mvc.MultipartFormData
import play.api.test.Helpers.{route, _}
import play.api.test.{FakeHeaders, FakeRequest, Helpers}
import play.mvc.Http.Status.OK
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import utils.WithWireMock

import scala.collection.JavaConversions._
import scala.concurrent.duration._

class NotificationSenderISpec
    extends UnitSpec
    with WithFakeApplication
    with WithWireMock
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with GivenWhenThen
    with Eventually {

  implicit override val patienceConfig =
    PatienceConfig(timeout = scaled(Span(5, Seconds)), interval = scaled(Span(300, Millis)))

  override val wiremockPort: Int = 9570

  implicit val actorSystem: ActorSystem        = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val timeout: akka.util.Timeout      = 10.seconds

  "UpscanStub" should {
    "initiate a request, upload a file, make a callback, and download a non-infected file" in {

      stubFor(
        post(urlPathEqualTo("/upscan/callback"))
          .willReturn(aResponse().withStatus(OK)))

      Given("a valid initiate request")
      val postBodyJson = Json.parse("""
          |{
          |	"callbackUrl": "http://localhost:9570/callback",
          |	"minimumFileSize" : 0,
          |	"maximumFileSize" : 1024,
          |	"expectedContentType": "application/xml"
          |}
        """.stripMargin)

      val initiateRequest =
        FakeRequest(Helpers.POST, "/upscan/initiate", FakeHeaders(), postBodyJson)

      When("a request is posted to the /initiate endpoint")
      val initiateResponse = route(fakeApplication, initiateRequest).get
      status(initiateResponse) shouldBe 200
      val preparedUpload: PreparedUpload = jsonBodyOf(initiateResponse).as[PreparedUpload]

      val uploadUrl = preparedUpload.uploadRequest.href.replace("http://", "")
      val formFields = preparedUpload.uploadRequest.fields.map(field => (field._1, Seq(field._2))) +
        ("x-amz-meta-callback-url" -> Seq(s"http://localhost:$wiremockPort/upscan/callback"))

      val fileReference = preparedUpload.uploadRequest.fields.get("key").get

      And("an uploaded request is posted to the returned /upload endpoint")
      val file: File = Files.createTempFile(Paths.get("/tmp"), "my-it-file", "txt").toFile
      Files.write(file.toPath, "End to end notification test contents".getBytes)

      val filePart =
        new MultipartFormData.FilePart[TemporaryFile]("file", "my-it-file.txt", None, new TemporaryFile(file))
      val postBodyForm: MultipartFormData[TemporaryFile] = new MultipartFormData[TemporaryFile](
        dataParts = formFields,
        files     = Seq(filePart),
        badParts  = Nil
      )

      val uploadRequest   = FakeRequest(Helpers.POST, uploadUrl, FakeHeaders(), postBodyForm)
      implicit val writer = utils.MultipartFormDataWritable.writeable
      val uploadResponse  = route(fakeApplication, uploadRequest).get
      status(uploadResponse) shouldBe 204

      And("a POST callback is received on the supplied callback URL")
      eventually {
        val expectedCallback = Json
          .obj(
            "reference"   -> fileReference,
            "downloadUrl" -> s"http:/upscan/download/$fileReference",
            "fileStatus"  -> "READY"
          )
          .toString
        verify(
          1,
          postRequestedFor(urlEqualTo("/upscan/callback")).withRequestBody(containing(expectedCallback.toString)))

        Then("the expected file should be available for download from the URL in the callback body")
        val callbackBody = getAllServeEvents.toList.head.getRequest.getBodyAsString
        val downloadUrl  = (Json.parse(callbackBody) \ "downloadUrl").as[String].replace("http:", "")

        val downloadRequest  = FakeRequest(Helpers.GET, downloadUrl)
        val downloadResponse = route(fakeApplication, downloadRequest).get

        status(downloadResponse) shouldBe 200
        val downloadContents: String = bodyOf(downloadResponse)
        downloadContents shouldBe "End to end notification test contents"
      }
    }

    "initiate a request, upload a file, make a callback, and report the error for an infected file" in {

      stubFor(
        post(urlPathEqualTo("/upscan/callback"))
          .willReturn(aResponse().withStatus(OK)))

      Given("a valid initiate request")
      val postBodyJson = Json.parse("""
                                      |{
                                      |	"callbackUrl": "http://localhost:9570/callback",
                                      |	"minimumFileSize" : 0,
                                      |	"maximumFileSize" : 1024,
                                      |	"expectedContentType": "application/xml"
                                      |}
                                    """.stripMargin)

      val initiateRequest =
        FakeRequest(Helpers.POST, "/upscan/initiate", FakeHeaders(), postBodyJson)

      When("a request is posted to the /initiate endpoint")
      val initiateResponse = route(fakeApplication, initiateRequest).get
      status(initiateResponse) shouldBe 200
      val preparedUpload: PreparedUpload = jsonBodyOf(initiateResponse).as[PreparedUpload]

      val uploadUrl = preparedUpload.uploadRequest.href.replace("http://", "")
      val formFields = preparedUpload.uploadRequest.fields.map(field => (field._1, Seq(field._2))) +
        ("x-amz-meta-callback-url" -> Seq(s"http://localhost:$wiremockPort/upscan/callback"))

      val fileReference = preparedUpload.uploadRequest.fields.get("key").get

      And("an uploaded request is posted to the returned /upload endpoint")
      val infectedContents = """X5O!P%@AP[4\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*"""
      val file: File       = Files.createTempFile(Paths.get("/tmp"), "my-infected-file", "txt").toFile
      Files.write(file.toPath, infectedContents.getBytes)

      val filePart =
        new MultipartFormData.FilePart[TemporaryFile]("file", "my-infected-file", None, new TemporaryFile(file))
      val postBodyForm: MultipartFormData[TemporaryFile] = new MultipartFormData[TemporaryFile](
        dataParts = formFields,
        files     = Seq(filePart),
        badParts  = Nil
      )

      val uploadRequest   = FakeRequest(Helpers.POST, uploadUrl, FakeHeaders(), postBodyForm)
      implicit val writer = utils.MultipartFormDataWritable.writeable
      val uploadResponse  = route(fakeApplication, uploadRequest).get
      status(uploadResponse) shouldBe 204

      And("a POST callback is received on the supplied callback URL detailing the infected file")
      eventually {
        val expectedCallback = Json
          .obj(
            "reference"  -> fileReference,
            "details"    -> "Eicar-Test-Signature",
            "fileStatus" -> "FAILED"
          )
          .toString
        verify(
          1,
          postRequestedFor(urlEqualTo("/upscan/callback")).withRequestBody(containing(expectedCallback.toString)))
      }
    }
  }
}
