package controllers

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import model.PreparedUpload
import org.scalatest.GivenWhenThen
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.HeaderNames.USER_AGENT
import play.api.http.Status
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers.{contentAsJson, route}
import play.api.test.{FakeHeaders, FakeRequest, Helpers}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.duration._

class InitiateControllerISpec extends UnitSpec with GuiceOneAppPerSuite with GivenWhenThen {

  implicit val actorSystem: ActorSystem        = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val timeout: akka.util.Timeout      = 10.seconds

  val requestHeaders = FakeHeaders(Seq((USER_AGENT, "InitiateControllerISpec")))

  "InitiateController /initiate" should {

    "respond with expected success JSON when passed a valid minimal request" in {
      Given("a valid request JSON body")
      val postBodyJson = Json.parse(
        """
          |{
          |	"callbackUrl": "http://localhost:9570/callback"
          |}
        """.stripMargin)

      val initiateRequest =
        FakeRequest(Helpers.POST, "/upscan/initiate", requestHeaders, postBodyJson)

      When("a request is posted to the /initiate endpoint")
      val initiateResponse = route(app, initiateRequest).get

      Then("a successful response is returned")
      status(initiateResponse) shouldBe 200

      And("the response body contains expected JSON")
      val responseJson = contentAsJson(initiateResponse)
      responseJson.validate[PreparedUpload].isSuccess shouldBe true

      (responseJson \ "uploadRequest" \ "href").as[String] shouldBe "http:///upscan/upload"
      (responseJson \ "uploadRequest" \ "fields" \ "x-amz-meta-callback-url")
        .as[String] shouldBe "http://localhost:9570/callback"
      (responseJson \ "uploadRequest" \ "fields" \ "x-amz-meta-consuming-service")
        .as[String] shouldBe "InitiateControllerISpec"
      (responseJson \ "reference").as[String] shouldBe (responseJson \ "uploadRequest" \ "fields" \ "key").as[String]
    }

    "respond with expected success JSON when passed a valid request" in {
      Given("a valid request JSON body")
      val postBodyJson = Json.parse(
        """
          |{
          |	"callbackUrl": "http://localhost:9570/callback",
          |	"minimumFileSize" : 0,
          |	"maximumFileSize" : 1024,
          |	"expectedMimeType": "application/xml",
          |	"successRedirect" : "https://localhost:8080"
          |}
        """.stripMargin)

      val initiateRequest =
        FakeRequest(Helpers.POST, "/upscan/initiate", requestHeaders, postBodyJson)

      When("a request is posted to the /initiate endpoint")
      val initiateResponse = route(app, initiateRequest).get

      Then("a successful response is returned")
      status(initiateResponse) shouldBe 200

      And("the response body contains expected JSON")
      val responseJson = contentAsJson(initiateResponse)
      responseJson.validate[PreparedUpload].isSuccess shouldBe true

      (responseJson \ "uploadRequest" \ "href").as[String] shouldBe "http:///upscan/upload"
      (responseJson \ "uploadRequest" \ "fields" \ "x-amz-meta-callback-url")
        .as[String] shouldBe "http://localhost:9570/callback"
      (responseJson \ "uploadRequest" \ "fields" \ "x-amz-meta-consuming-service")
        .as[String] shouldBe "InitiateControllerISpec"
      (responseJson \ "reference").as[String] shouldBe (responseJson \ "uploadRequest" \ "fields" \ "key").as[String]
      (responseJson \ "uploadRequest" \ "fields" \ "success_action_redirect")
        .as[String] shouldBe "https://localhost:8080"
    }

    "respond with 403 when the User Agent header is missing from the request" in {
      val postBodyJson = Json.parse(
        """
          |{
          |	"callbackUrl": "http://localhost:9570/callback",
          |	"minimumFileSize" : 0,
          |	"maximumFileSize" : 1024,
          |	"expectedMimeType": "application/xml"
          |}
        """.stripMargin)

      Given("a request without a User Agent header")
      val initiateRequest =
        FakeRequest(Helpers.POST, "/upscan/initiate", FakeHeaders(), postBodyJson)

      When("a request is posted to the /initiate endpoint")
      val initiateResponse = route(app, initiateRequest).get

      Then("the response should indicate the request is Forbidden")
      status(initiateResponse) shouldBe 403
    }

    "respond with expected error JSON when passed a invalid request" in {
      Given("an invalid request JSON body")
      val postBodyJson = Json.parse(
        """
          |{
          |	"someKey": "someValue",
          |	"someOtherKey" : 0,
          |	"expectedMimeType": "application/xml"
          |}
        """.stripMargin)

      val initiateRequest =
        FakeRequest(Helpers.POST, "/upscan/initiate", requestHeaders, postBodyJson)

      When("a request is posted to the /initiate endpoint")
      val initiateResponse = route(app, initiateRequest).get

      Then("a Bad Request response is returned")
      status(initiateResponse) shouldBe 400

      And("the response body contains expected error message")
      val responseBody: String = bodyOf(initiateResponse)
      responseBody shouldBe "Invalid UploadSettings payload: List((/callbackUrl,List(ValidationError(List(error.path.missing),WrappedArray()))))"
    }

    "respond with supplied file size constraints in the policy" in {
      Given("a valid request with file size constraints")
      val postBodyJson = Json.obj(
        "callbackUrl" -> "http://localhost:9570/callback",
        "minimumFileSize" -> 123,
        "maximumFileSize" -> 456,
        "expectedContentType" -> "pdf"
      )

      val initiateRequest = FakeRequest(Helpers.POST, "/upscan/initiate", requestHeaders, postBodyJson)

      When("the request is POSTed to /upscan/initiate")
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
        "callbackUrl" -> "http://localhost:9570/callback",
        "expectedContentType" -> "pdf"
      )

      val initiateRequest = FakeRequest(Helpers.POST, "/upscan/initiate", requestHeaders, postBodyJson)

      When("the request is POSTed to /upscan/initiate")
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
        "callbackUrl" -> "http://localhost:9570/callback",
        "minimumFileSize" -> -10,
        "maximumFileSize" -> 104857600*10,
        "expectedContentType" -> "pdf"
      )

      val initiateRequest = FakeRequest(Helpers.POST, "/upscan/initiate", requestHeaders, postBodyJson)

      When("the request is POSTed to /upscan/initiate")
      val initiateResponse = route(app, initiateRequest).get

      Then("the response status is 400")
      status(initiateResponse) shouldBe Status.BAD_REQUEST
    }

    "respond with 400 when supplied min value is greater than the max value" in {
      Given("an invalid request with invalid file size limits")
      val postBodyJson = Json.obj(
        "callbackUrl" -> "http://localhost:9570/callback",
        "minimumFileSize" -> 100,
        "maximumFileSize" -> 90,
        "expectedContentType" -> "pdf"
      )

      val initiateRequest = FakeRequest(Helpers.POST, "/upscan/initiate", requestHeaders, postBodyJson)

      When("the request is POSTed to /upscan/initiate")
      val initiateResponse = route(app, initiateRequest).get

      Then("the response status is 400")
      status(initiateResponse) shouldBe Status.BAD_REQUEST
    }
  }

  private def withMinMaxFileSizesInPolicyConditions[T](responseBodyJson: JsValue)(block: (Option[Long], Option[Long]) => T): T = {
    import utils.Implicits.Base64StringOps

    val maybeBase64policy = (responseBodyJson \ "uploadRequest" \ "fields" \ "policy").asOpt[String]

    val policy: String = maybeBase64policy.get.base64decode

    val policyJson: JsValue = Json.parse(policy)

    val conditions = (policyJson \ "conditions")(0)

    conditions(0).asOpt[String] shouldBe Some("content-length-range")

    block(conditions(1).asOpt[Long], conditions(2).asOpt[Long])
  }
}
