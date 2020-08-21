package services

import java.nio.file.Files
import java.time.{Clock, Instant, ZoneId}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.github.tomakehurst.wiremock.client.WireMock._
import it.utils
import it.utils.{MultipartFormDataWritable, WithWireMock}
import model.initiate.PrepareUploadResponse
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, GivenWhenThen}
import play.api.http.HeaderNames.USER_AGENT
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.Files.{SingletonTemporaryFileCreator, TemporaryFile}
import play.api.libs.json.Json
import play.api.mvc.MultipartFormData
import play.api.test.{FakeHeaders, FakeRequest}
import play.api.test.Helpers.{contentAsJson, contentAsString, defaultAwaitTimeout, route, status, writeableOf_AnyContentAsEmpty}
import play.api.test.Helpers
import play.api.{Application, Play}
import play.mvc.Http.Status.OK

import scala.collection.JavaConverters._

class NotificationSenderISpec
    extends AnyWordSpec
    with Matchers
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

  val requestHeaders = FakeHeaders(Seq((USER_AGENT, "InitiateControllerISpec")))

  lazy val fakeApplication: Application = new GuiceApplicationBuilder()
    .overrides(bind[Clock].to[NotificationSenderClock])
    .build()

  override def beforeAll() {
    super.beforeAll()
    Play.start(fakeApplication)
  }

  override def afterAll() {
    super.afterAll()
    Play.stop(fakeApplication)
  }

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
        FakeRequest(Helpers.POST, "/upscan/initiate", requestHeaders, postBodyJson)

      When("a request is posted to the /initiate endpoint")
      val initiateResponse = route(fakeApplication, initiateRequest).get
      status(initiateResponse) shouldBe 200
      val response: PrepareUploadResponse =
        contentAsJson(initiateResponse).as[PrepareUploadResponse](PrepareUploadResponse.format)

      val uploadUrl = response.uploadRequest.href.replace("http://", "")
      val formFields = response.uploadRequest.fields.map(field => (field._1, Seq(field._2))) +
        ("x-amz-meta-callback-url" -> Seq(s"http://localhost:$wiremockPort/upscan/callback"))

      val fileReference = response.uploadRequest.fields("key")

      And("an uploaded request is posted to the returned /upload endpoint")
      val file = SingletonTemporaryFileCreator.create("my-it-file", ".txt")

      Files.write(file.toPath, "End to end notification test contents".getBytes)

      val filePart =
        new MultipartFormData.FilePart[TemporaryFile]("file", "my-it-file.pdf", None, file)
      val postBodyForm: MultipartFormData[TemporaryFile] = new MultipartFormData[TemporaryFile](
        dataParts = formFields,
        files     = Seq(filePart),
        badParts  = Nil
      )

      val uploadRequest   = FakeRequest(Helpers.POST, uploadUrl, FakeHeaders(), postBodyForm)
      implicit val writer = MultipartFormDataWritable.writeable
      val uploadResponse  = route(fakeApplication, uploadRequest).get
      status(uploadResponse) shouldBe 204

      And("a POST callback is received on the supplied callback URL")
      eventually {
        val expectedCallback = Json
          .obj(
            "reference"  -> fileReference,
            "fileStatus" -> "READY",
            "uploadDetails" -> Json.obj(
              "uploadTimestamp" -> "2018-04-24T09:30:00Z",
              "checksum"        -> "2f8a8ceeec0dc64ffaca269f55e74699bee881749de20cdb9631f8fcc72f8a62",
              "fileMimeType"    -> "application/pdf",
              "fileName"        -> "my-it-file.pdf"
              //Excluding downloadUrl
            )
          )
          .toString
        verify(
          1,
          postRequestedFor(urlEqualTo("/upscan/callback")).withRequestBody(equalToJson(expectedCallback, true, true)))

        Then("the expected file should be available for download from the URL in the callback body")
        val callbackBody = getAllServeEvents.asScala.toList.head.getRequest.getBodyAsString
        val downloadUrl  = (Json.parse(callbackBody) \ "downloadUrl").as[String].replace("http:", "")

        val downloadRequest  = FakeRequest(Helpers.GET, downloadUrl)
        val downloadResponse = route(fakeApplication, downloadRequest).get

        status(downloadResponse) shouldBe 200
        val downloadContents: String = contentAsString(downloadResponse)
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
        FakeRequest(Helpers.POST, "/upscan/initiate", requestHeaders, postBodyJson)

      When("a request is posted to the /initiate endpoint")
      val initiateResponse = route(fakeApplication, initiateRequest).get
      status(initiateResponse) shouldBe 200
      val response: PrepareUploadResponse =
        contentAsJson(initiateResponse).as[PrepareUploadResponse](PrepareUploadResponse.format)

      val uploadUrl = response.uploadRequest.href.replace("http://", "")
      val formFields = response.uploadRequest.fields.map(field => (field._1, Seq(field._2))) +
        ("x-amz-meta-callback-url" -> Seq(s"http://localhost:$wiremockPort/upscan/callback"))

      val fileReference = response.uploadRequest.fields("key")

      And("an uploaded request is posted to the returned /upload endpoint")
      val infectedContents = """X5O!P%@AP[4\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*"""
      val file       = SingletonTemporaryFileCreator.create("my-infected-file", ".txt")
      Files.write(file.toPath, infectedContents.getBytes)

      val filePart =
        new MultipartFormData.FilePart[TemporaryFile]("file", "my-infected-file", None, file)
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
            "fileStatus" -> "FAILED",
            "failureDetails" -> Json.obj(
              "failureReason" -> "QUARANTINE",
              "message"       -> "Eicar-Test-Signature"
            )
          )
          .toString
        verify(
          1,
          postRequestedFor(urlEqualTo("/upscan/callback")).withRequestBody(containing(expectedCallback.toString)))
      }
    }
  }
}

class NotificationSenderClock extends Clock {
  override def withZone(zone: ZoneId): Clock = ???

  override def getZone: ZoneId = ???

  override def instant(): Instant = Instant.parse("2018-04-24T09:30:00Z")
}
