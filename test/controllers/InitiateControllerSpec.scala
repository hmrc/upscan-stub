package controllers

import akka.actor.ActorSystem
import akka.stream._
import model.Reference
import model.initiate.{PrepareUploadResponse, UploadFormTemplate}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{GivenWhenThen, Matchers}
import play.api.http.HeaderNames.USER_AGENT
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.Results.Ok
import play.api.mvc.{Action, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.contentAsString
import services.PrepareUploadService
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class InitiateControllerSpec extends UnitSpec with Matchers with GivenWhenThen with MockitoSugar {

  implicit val actorSystem: ActorSystem        = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val timeout: akka.util.Timeout      = 10.seconds

  val requestHeaders = (USER_AGENT, "InitiateControllerSpec")

  "Upscan Initiate V1" should {
    behave like upscanInitiateTests(_.prepareUploadV1())
  }

  "Upscan Initiate V2" should {
    val extraRequestFields = Json
      .obj("successRedirect" -> "https://www.example.com/nextpage", "errorRedirect" -> "https://www.example.com/error")

    behave like upscanInitiateTests(_.prepareUploadV2(), extraRequestFields)
  }

  private def upscanInitiateTests( // scalastyle:ignore
    route: InitiateController => Action[JsValue],
    extraRequestFields: JsObject = JsObject(Seq())): Unit = {

    "return expected JSON for prepare upload when passed valid request" in {
      Given("a request containing a valid JSON body")
      val validJsonBody: JsValue = Json.obj(
        "callbackUrl"      -> "https://myservice.com/callback",
        "minimumFileSize"  -> 0,
        "maximumFileSize"  -> 1024,
        "expectedMimeType" -> "application/xml"
      ) ++ extraRequestFields

      val request = FakeRequest().withHeaders(requestHeaders).withBody(validJsonBody)

      When("the prepare upload method is called")
      val preparedUpload = PrepareUploadResponse(
        Reference("abcd-efgh-1234"),
        UploadFormTemplate("http://myservice.com/upload", Map.empty)
      )
      val prepareService = mock[PrepareUploadService]
      Mockito.when(prepareService.prepareUpload(any(), any())).thenReturn(preparedUpload)
      val controller             = new InitiateController(prepareService)(ExecutionContext.Implicits.global)
      val result: Future[Result] = route(controller)(request)

      Then("a successful HTTP response should be returned")
      val responseStatus = status(result)
      responseStatus shouldBe 200

      And("the response should contain the expected JSON body")
      val body: JsValue = jsonBodyOf(result)
      body shouldBe Json.obj(
        "reference" -> "abcd-efgh-1234",
        "uploadRequest" -> Json.obj(
          "href"   -> "http://myservice.com/upload",
          "fields" -> Json.obj()
        )
      )
    }

    "return expected error for prepare upload when passed invalid JSON request" in {
      Given("a request containing an invalid JSON body")
      val invalidJsonBody: JsValue = Json.parse("""
          |{
          |	"someKey": "someValue",
          |	"someOtherKey" : 12345
          |}
        """.stripMargin)

      val request = FakeRequest().withHeaders(requestHeaders).withBody(invalidJsonBody)

      When("the prepare upload method is called")
      val prepareService         = mock[PrepareUploadService]
      val controller             = new InitiateController(prepareService)(ExecutionContext.Implicits.global)
      val result: Future[Result] = route(controller)(request)

      Then("a BadRequest response should be returned")
      val responseStatus = status(result)
      responseStatus shouldBe 400
    }

    "return expected error for prepare upload when passed non-JSON request" in {
      Given("a request containing an invalid JSON body")
      val invalidStringBody: String = "This is an invalid body"
      val request                   = FakeRequest().withHeaders(requestHeaders).withBody(invalidStringBody)

      When("the prepare upload method is called")
      val prepareService         = mock[PrepareUploadService]
      val controller             = new InitiateController(prepareService)(ExecutionContext.Implicits.global)
      val result: Future[Result] = route(controller)(request).run()

      Then("an Invalid Media Type response should be returned")
      val responseStatus = status(result)
      responseStatus shouldBe 415
    }

    "allow https callback urls" in {
      val controller = new InitiateController(mock[PrepareUploadService])(ExecutionContext.Implicits.global)

      val result = controller.withAllowedCallbackProtocol("https://my.callback.url") {
        Future.successful(Ok)
      }

      status(result) shouldBe 200
    }

    "disallow http callback urls" in {
      val controller = new InitiateController(mock[PrepareUploadService])(ExecutionContext.Implicits.global)

      val result = controller.withAllowedCallbackProtocol("http://my.callback.url") {
        Future.failed(new RuntimeException("This block should not have been invoked."))
      }

      status(result)          shouldBe 400
      contentAsString(result) should include("Invalid callback url protocol")
    }

    "disallow invalidly formatted callback urls" in {
      val controller = new InitiateController(mock[PrepareUploadService])(ExecutionContext.Implicits.global)

      val result = controller.withAllowedCallbackProtocol("123") {
        Future.failed(new RuntimeException("This block should not have been invoked."))
      }
      status(result)          shouldBe 400
      contentAsString(result) should include("Invalid callback url format")
    }
  }
}
