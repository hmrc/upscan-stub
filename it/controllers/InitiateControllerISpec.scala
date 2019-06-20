package controllers

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import model.initiate.PrepareUploadResponse
import org.scalatest.GivenWhenThen
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.HeaderNames.USER_AGENT
import play.api.http.Status
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.test.Helpers.{contentAsJson, route}
import play.api.test.{FakeHeaders, FakeRequest, Helpers}
import uk.gov.hmrc.play.test.UnitSpec
import play.api.libs.json._

import scala.concurrent.duration._

class InitiateControllerISpec extends UnitSpec with GuiceOneAppPerSuite with GivenWhenThen {

  implicit val actorSystem: ActorSystem        = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val timeout: akka.util.Timeout      = 10.seconds

  val requestHeaders = FakeHeaders(Seq((USER_AGENT, "InitiateControllerISpec")))

  "Upscan Initiate V1" should {
    behave like upscanInitiateTests("/upscan/initiate", "http:///upscan/upload")
  }

  "Upscan Initiate V2" should {
    val extraRequestFields = Json
      .obj("successRedirect" -> "https://www.example.com/nextpage", "errorRedirect" -> "https://www.example.com/error")

    behave like upscanInitiateTests("/upscan/v2/initiate", "http:///upscan/upload-proxy", extraRequestFields)
  }

  "InitiateController /initiate" should {}

  private def upscanInitiateTests(uri: String, href: String, extraRequestFields: JsObject = JsObject(Seq())): Unit = { // scalastyle:ignore

    "respond with expected success JSON when passed a valid minimal request" in {
      Given("a valid request JSON body")
      val postBodyJson = Json.obj("callbackUrl" -> "http://localhost:9570/callback") ++ extraRequestFields

      val initiateRequest =
        FakeRequest(Helpers.POST, uri, requestHeaders, postBodyJson)

      When(s"there is a POST request to $uri")
      val initiateResponse = route(app, initiateRequest).get

      Then("a successful response is returned")
      status(initiateResponse) shouldBe 200

      And("the response body contains expected JSON")
      val responseJson = contentAsJson(initiateResponse)
      responseJson
        .validate[PrepareUploadResponse](PrepareUploadResponse.format)
        .isSuccess shouldBe true

      (responseJson \ "uploadRequest" \ "href").as[String] shouldBe href
      (responseJson \ "uploadRequest" \ "fields" \ "x-amz-meta-callback-url")
        .as[String] shouldBe "http://localhost:9570/callback"
      (responseJson \ "uploadRequest" \ "fields" \ "x-amz-meta-consuming-service")
        .as[String]                           shouldBe "InitiateControllerISpec"
      (responseJson \ "reference").as[String] shouldBe (responseJson \ "uploadRequest" \ "fields" \ "key").as[String]
    }

    "respond with expected success JSON when passed a valid request" in {
      Given("a valid request JSON body")
      val postBodyJson = Json.obj(
        "callbackUrl"      -> "http://localhost:9570/callback",
        "minimumFileSize"  -> 0,
        "maximumFileSize"  -> 1024,
        "expectedMimeType" -> "application/xml",
        "successRedirect"  -> "https://www.example.com/nextpage"
      ) ++ extraRequestFields

      val initiateRequest =
        FakeRequest(Helpers.POST, uri, requestHeaders, postBodyJson)

      When(s"there is a POST request to $uri")
      val initiateResponse = route(app, initiateRequest).get

      Then("a successful response is returned")
      status(initiateResponse) shouldBe 200

      And("the response body contains expected JSON")
      val responseJson = contentAsJson(initiateResponse)
      responseJson
        .validate[PrepareUploadResponse](PrepareUploadResponse.format)
        .isSuccess shouldBe true

      (responseJson \ "uploadRequest" \ "href").as[String] shouldBe href
      (responseJson \ "uploadRequest" \ "fields" \ "x-amz-meta-callback-url")
        .as[String] shouldBe "http://localhost:9570/callback"
      (responseJson \ "uploadRequest" \ "fields" \ "x-amz-meta-consuming-service")
        .as[String]                           shouldBe "InitiateControllerISpec"
      (responseJson \ "reference").as[String] shouldBe (responseJson \ "uploadRequest" \ "fields" \ "key").as[String]
      (responseJson \ "uploadRequest" \ "fields" \ "success_action_redirect")
        .as[String] shouldBe "https://www.example.com/nextpage"
    }

    "respond with 403 when the User Agent header is missing from the request" in {
      val postBodyJson = Json.obj(
        "callbackUrl"      -> "http://localhost:9570/callback",
        "minimumFileSize"  -> 0,
        "maximumFileSize"  -> 1024,
        "expectedMimeType" -> "application/xml"
      ) ++ extraRequestFields

      Given("a request without a User Agent header")
      val initiateRequest =
        FakeRequest(Helpers.POST, uri, FakeHeaders(), postBodyJson)

      When(s"there is a POST request to $uri")
      val initiateResponse = route(app, initiateRequest).get

      Then("the response should indicate the request is Forbidden")
      status(initiateResponse) shouldBe 403
    }

    "respond with expected error JSON when passed a invalid request" in {
      Given("an invalid request JSON body")
      val postBodyJson = Json.obj(
        "someKey"          -> "someValue",
        "someOtherKey"     -> 0,
        "expectedMimeType" -> "application/xml"
      ) ++ extraRequestFields

      val initiateRequest =
        FakeRequest(Helpers.POST, uri, requestHeaders, postBodyJson)

      When(s"there is a POST request to $uri")
      val initiateResponse = route(app, initiateRequest).get

      Then("a Bad Request response is returned")
      status(initiateResponse) shouldBe 400

      And("the response body contains expected error message")
      val responseBody: String = bodyOf(initiateResponse)
      responseBody should include(
        "payload: List((/callbackUrl,List(ValidationError(List(error.path.missing),WrappedArray()))))")
    }

    "respond with supplied file size constraints in the policy" in {
      Given("a valid request with file size constraints")
      val postBodyJson = Json.obj(
        "callbackUrl"         -> "http://localhost:9570/callback",
        "minimumFileSize"     -> 123,
        "maximumFileSize"     -> 456,
        "expectedContentType" -> "pdf"
      ) ++ extraRequestFields

      val initiateRequest = FakeRequest(Helpers.POST, uri, requestHeaders, postBodyJson)

      When(s"there is a POST request to $uri")
      val initiateResponse = route(app, initiateRequest).get

      Then("the response status is 200")
      status(initiateResponse) shouldBe Status.OK

      And("the response policy includes the supplied file size constraints")
      withMinMaxFileSizesInPolicyConditions(contentAsJson(initiateResponse)) { (min, max) =>
        min shouldBe Some(123)
        max shouldBe Some(456)
      }
    }

    "respond with default file size constraints in the policy when supplied values are missing" in {
      Given("a valid request with no file size constraints")
      val postBodyJson = Json.obj(
        "callbackUrl"         -> "http://localhost:9570/callback",
        "expectedContentType" -> "pdf"
      ) ++ extraRequestFields

      val initiateRequest = FakeRequest(Helpers.POST, uri, requestHeaders, postBodyJson)

      When(s"there is a POST request to $uri")
      val initiateResponse = route(app, initiateRequest).get

      Then("the response status is 200")
      status(initiateResponse) shouldBe Status.OK

      And("the response policy includes the default file size constraints")
      withMinMaxFileSizesInPolicyConditions(contentAsJson(initiateResponse)) { (min, max) =>
        min shouldBe Some(0)
        max shouldBe Some(104857600)
      }
    }

    "respond with 400 when supplied values are outside of expected limits" in {
      Given("an invalid request with invalid file size limits")
      val postBodyJson = Json.obj(
        "callbackUrl"         -> "http://localhost:9570/callback",
        "minimumFileSize"     -> -10,
        "maximumFileSize"     -> 104857600 * 10,
        "expectedContentType" -> "pdf"
      ) ++ extraRequestFields

      val initiateRequest = FakeRequest(Helpers.POST, uri, requestHeaders, postBodyJson)

      When(s"there is a POST request to $uri")
      val initiateResponse = route(app, initiateRequest).get

      Then("the response status is 400")
      status(initiateResponse) shouldBe Status.BAD_REQUEST
    }

    "respond with 400 when supplied min value is greater than the max value" in {
      Given("an invalid request with invalid file size limits")
      val postBodyJson = Json.obj(
        "callbackUrl"         -> "http://localhost:9570/callback",
        "minimumFileSize"     -> 100,
        "maximumFileSize"     -> 90,
        "expectedContentType" -> "pdf"
      ) ++ extraRequestFields

      val initiateRequest = FakeRequest(Helpers.POST, uri, requestHeaders, postBodyJson)

      When(s"there is a POST request to $uri")
      val initiateResponse = route(app, initiateRequest).get

      Then("the response status is 400")
      status(initiateResponse) shouldBe Status.BAD_REQUEST
    }
  }

  private def withMinMaxFileSizesInPolicyConditions[T](responseBodyJson: JsValue)(
    block: (Option[Long], Option[Long]) => T): T = {
    import utils.Implicits.Base64StringOps

    val maybeBase64policy = (responseBodyJson \ "uploadRequest" \ "fields" \ "policy").asOpt[String]

    val policy: String = maybeBase64policy.get.base64decode()

    val policyJson: JsValue = Json.parse(policy)

    val conditions = (policyJson \ "conditions")(0)

    conditions(0).asOpt[String] shouldBe Some("content-length-range")

    block(conditions(1).asOpt[Long], conditions(2).asOpt[Long])
  }
}
