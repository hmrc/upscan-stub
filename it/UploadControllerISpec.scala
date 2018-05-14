import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import model.PreparedUpload
import org.scalatest.GivenWhenThen
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.Json
import play.api.test.Helpers.{contentAsJson, route}
import play.api.test.{FakeHeaders, FakeRequest, Helpers}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.duration._

class UploadControllerISpec extends UnitSpec with GuiceOneAppPerSuite with GivenWhenThen {

  implicit val actorSystem: ActorSystem        = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val timeout: akka.util.Timeout      = 10.seconds

  "InitiateController" should {

    "respond with expected success JSON when passed a valid request" in {
      Given("a valid request JSON body")
      val postBodyJson = Json.parse("""
          |{
          |	"callbackUrl": "http://localhost:9570/callback",
          |	"minimumFileSize" : 0,
          |	"maximumFileSize" : 1024,
          |	"expectedMimeType": "application/xml"
          |}
        """.stripMargin)

      val initiateRequest =
        FakeRequest(Helpers.POST, "/upscan/initiate", FakeHeaders(), postBodyJson)

      When("a request is posted to the /initiate endpoint")
      val initiateResponse = route(app, initiateRequest).get

      Then("a successful response is returned")
      status(initiateResponse) shouldBe 200

      And("the response body contains expected JSON")
      val responseJson = contentAsJson(initiateResponse)
      responseJson.validate[PreparedUpload].isSuccess shouldBe true

      (responseJson \ "uploadRequest" \ "href").as[String] shouldBe "http:///upscan/upload"
      (responseJson \ "uploadRequest" \ "fields" \ "x-amz-meta-callback-url")
        .as[String]                           shouldBe "http://localhost:9570/callback"
      (responseJson \ "reference").as[String] shouldBe (responseJson \ "uploadRequest" \ "fields" \ "key").as[String]
    }

    "respond with expected error JSON when passed a invalid request" in {
      Given("an invalid request JSON body")
      val postBodyJson = Json.parse("""
                                      |{
                                      |	"someKey": "someValue",
                                      |	"someOtherKey" : 0,
                                      |	"expectedMimeType": "application/xml"
                                      |}
                                    """.stripMargin)

      val initiateRequest =
        FakeRequest(Helpers.POST, "/upscan/initiate", FakeHeaders(), postBodyJson)

      When("a request is posted to the /initiate endpoint")
      val initiateResponse = route(app, initiateRequest).get

      Then("a Bad Request response is returned")
      status(initiateResponse) shouldBe 400

      And("the response body contains expected error message")
      val responseBody: String = bodyOf(initiateResponse)
      responseBody shouldBe "Invalid UploadSettings payload: List((/callbackUrl,List(ValidationError(List(error.path.missing),WrappedArray()))))"
    }
  }
}
