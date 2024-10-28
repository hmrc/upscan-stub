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
import org.scalatest.{GivenWhenThen, OptionValues}
import org.scalatest.matchers.Matcher
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.HeaderNames.USER_AGENT
import play.api.http.MimeTypes.XML
import play.api.http.Status.{BAD_REQUEST, OK}
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.test.Helpers.{contentAsJson, contentAsString, route, status}
import play.api.test.{FakeHeaders, FakeRequest, Helpers}
import uk.gov.hmrc.upscanstub.model.initiate.PrepareUploadResponse
import uk.gov.hmrc.upscanstub.util.Base64StringUtils

import scala.concurrent.duration._

class InitiateControllerISpec
  extends AnyWordSpec
     with Matchers
     with GuiceOneAppPerSuite
     with GivenWhenThen
     with OptionValues:

  private given ActorSystem                   = ActorSystem()
  private given org.apache.pekko.util.Timeout = 10.seconds

  private val requestHeaders = FakeHeaders(Seq((USER_AGENT, "InitiateControllerISpec")))

  "Upscan Initiate V1" should {
    val requestUrl = "/upscan/initiate"
    val expectedUploadUrl = "http:///upscan/upload"

    behave like upscanInitiate(requestUrl, expectedUploadUrl)
    behave like upscanInitiateWithAllFields(requestUrl, expectedUploadUrl,
      responseFieldsMatcher = Some(not contain key("error_action_redirect")))
  }

  "Upscan Initiate V2" should {
    val requestUrl = "/upscan/v2/initiate"
    val expectedUploadUrl = "http:///upscan/upload-proxy"
    val errorRedirectUrl = "https://www.example.com/error"
    val extraRequestFields = Json.obj("errorRedirect" -> errorRedirectUrl)

    behave like upscanInitiate(requestUrl, expectedUploadUrl)
    behave like upscanInitiateWithAllFields(requestUrl, expectedUploadUrl, extraRequestFields,
      responseFieldsMatcher = Some(contain("error_action_redirect" -> errorRedirectUrl)))
  }

  private def upscanInitiate(uri: String, href: String): Unit = // scalastyle:ignore

    "respond with expected success JSON when passed a valid minimal request" in:
      Given("a valid request JSON body")
      val postBodyJson = Json.obj("callbackUrl" -> "http://localhost:9570/callback")

      val initiateRequest = FakeRequest(Helpers.POST, uri, requestHeaders, postBodyJson)

      When(s"there is a POST request to $uri")
      val initiateResponse = route(app, initiateRequest).get

      Then("a successful response is returned")
      status(initiateResponse) shouldBe OK

      And("the response body contains expected JSON")
      val responseJson = contentAsJson(initiateResponse)
      responseJson.validate[PrepareUploadResponse](PrepareUploadResponse.format).isSuccess shouldBe true

      (responseJson \ "uploadRequest" \ "href").as[String] shouldBe href

      val reference = (responseJson \ "reference").as[String]
      val fields = (responseJson \ "uploadRequest" \ "fields").as[Map[String, String]]
      fields.get("key") should contain (reference)
      fields.get("acl") should contain ("private")
      fields should contain key "policy"
      fields.get("x-amz-algorithm") should contain ("AWS4-HMAC-SHA256")
      fields should contain key "x-amz-credential"
      fields should contain key "x-amz-date"
      fields should contain key "x-amz-signature"
      fields.get("x-amz-meta-consuming-service") should contain ("InitiateControllerISpec")
      fields.get("x-amz-meta-callback-url") should contain ("http://localhost:9570/callback")
      fields.get("success_action_redirect") shouldBe empty
      fields.get("error_action_redirect") shouldBe empty
      fields.get("Content-Type") shouldBe empty

    "respond with Bad Request when the User-Agent header is missing from the request" in:
      val postBodyJson = Json.obj("callbackUrl" -> "http://localhost:9570/callback")

      Given("a request without a User-Agent header")
      val initiateRequest = FakeRequest(Helpers.POST, uri, FakeHeaders(), postBodyJson)

      When(s"there is a POST request to $uri")
      val initiateResponse = route(app, initiateRequest).get

      Then("the response should indicate the request is invalid")
      status(initiateResponse) shouldBe BAD_REQUEST

    "respond with expected error JSON when passed an invalid request" in:
      Given("an invalid request JSON body")
      val postBodyMissingCallbackUrlJson = Json.obj("someKey" -> "someValue")

      val initiateRequest = FakeRequest(Helpers.POST, uri, requestHeaders, postBodyMissingCallbackUrlJson)

      When(s"there is a POST request to $uri")
      val initiateResponse = route(app, initiateRequest).get

      Then("a Bad Request response is returned")
      status(initiateResponse) shouldBe BAD_REQUEST

      And("the response body contains expected error message")
      contentAsString(initiateResponse) should include(
        "payload: List((/callbackUrl,List(JsonValidationError(List(error.path.missing),ArraySeq()))))"
      )

    "respond with supplied file size constraints in the policy" in:
      Given("a valid request with file size constraints")
      val postBodyJson = Json.obj(
        "callbackUrl"         -> "http://localhost:9570/callback",
        "minimumFileSize"     -> 123,
        "maximumFileSize"     -> 456
      )

      val initiateRequest = FakeRequest(Helpers.POST, uri, requestHeaders, postBodyJson)

      When(s"there is a POST request to $uri")
      val initiateResponse = route(app, initiateRequest).get

      Then("a successful response is returned")
      status(initiateResponse) shouldBe OK

      And("the response policy includes the supplied file size constraints")
      withMinMaxFileSizesInPolicyConditions(contentAsJson(initiateResponse)): (min, max) =>
        min shouldBe Some(123)
        max shouldBe Some(456)

    "respond with default file size constraints in the policy when supplied values are missing" in:
      Given("a valid request with no file size constraints")
      val postBodyJson = Json.obj("callbackUrl" -> "http://localhost:9570/callback")

      val initiateRequest = FakeRequest(Helpers.POST, uri, requestHeaders, postBodyJson)

      When(s"there is a POST request to $uri")
      val initiateResponse = route(app, initiateRequest).get

      Then("a successful response is returned")
      status(initiateResponse) shouldBe OK

      And("the response policy includes the default file size constraints")
      withMinMaxFileSizesInPolicyConditions(contentAsJson(initiateResponse)): (min, max) =>
        min shouldBe Some(0)
        max shouldBe Some(104857600)

    "respond with Bad Request when supplied file size constraints are outside of expected limits" in:
      Given("an invalid request with invalid file size limits")
      val postBodyJson = Json.obj(
        "callbackUrl"         -> "http://localhost:9570/callback",
        "minimumFileSize"     -> -10,
        "maximumFileSize"     -> 104857600 * 10
      )

      val initiateRequest = FakeRequest(Helpers.POST, uri, requestHeaders, postBodyJson)

      When(s"there is a POST request to $uri")
      val initiateResponse = route(app, initiateRequest).get

      Then("a Bad Request response is returned")
      status(initiateResponse) shouldBe BAD_REQUEST

    "respond with Bad Request when supplied min value is greater than the max value" in:
      Given("an invalid request with invalid file size limits")
      val postBodyJson = Json.obj(
        "callbackUrl"         -> "http://localhost:9570/callback",
        "minimumFileSize"     -> 100,
        "maximumFileSize"     -> 90
      )

      val initiateRequest = FakeRequest(Helpers.POST, uri, requestHeaders, postBodyJson)

      When(s"there is a POST request to $uri")
      val initiateResponse = route(app, initiateRequest).get

      Then("a Bad Request response is returned")
      status(initiateResponse) shouldBe BAD_REQUEST

  private def upscanInitiateWithAllFields(
    uri                  : String,
    href                 : String,
    extraRequestFields   : JsObject                           = JsObject.empty,
    responseFieldsMatcher: Option[Matcher[Map[String, String]]]
  ): Unit = // scalastyle:ignore

    "respond with expected success JSON when passed a valid request with all fields" in:
      Given("a valid request JSON body")
      val postBodyJson = Json.obj(
        "callbackUrl"      -> "http://localhost:9570/callback",
        "minimumFileSize"  -> 0,
        "maximumFileSize"  -> 1024,
        "expectedContentType" -> XML,
        "successRedirect"  -> "https://www.example.com/nextpage"
      ) ++ extraRequestFields

      val initiateRequest = FakeRequest(Helpers.POST, uri, requestHeaders, postBodyJson)

      When(s"there is a POST request to $uri")
      val initiateResponse = route(app, initiateRequest).get

      Then("a successful response is returned")
      status(initiateResponse) shouldBe OK

      And("the response body contains expected JSON")
      val responseJson = contentAsJson(initiateResponse)
      responseJson.validate[PrepareUploadResponse](PrepareUploadResponse.format).isSuccess shouldBe true

      (responseJson \ "uploadRequest" \ "href").as[String] shouldBe href

      val reference = (responseJson \ "reference").as[String]
      val fields = (responseJson \ "uploadRequest" \ "fields").as[Map[String, String]]
      fields.get("key") should contain (reference)
      fields.get("acl") should contain ("private")
      fields should contain key "policy"
      fields.get("x-amz-algorithm") should contain ("AWS4-HMAC-SHA256")
      fields should contain key "x-amz-credential"
      fields should contain key "x-amz-date"
      fields should contain key "x-amz-signature"
      fields.get("x-amz-meta-consuming-service") should contain ("InitiateControllerISpec")
      fields.get("x-amz-meta-callback-url") should contain ("http://localhost:9570/callback")
      fields.get("success_action_redirect").value should startWith("https://www.example.com/nextpage?key=")
      responseFieldsMatcher.foreach(matchExpectations => fields should matchExpectations)

  private def withMinMaxFileSizesInPolicyConditions[T](
    responseBodyJson: JsValue
  )(
    block: (Option[Long], Option[Long]) => T
  ): T =
    val maybeBase64policy = (responseBodyJson \ "uploadRequest" \ "fields" \ "policy").asOpt[String]

    val policyJson: JsValue = Json.parse(Base64StringUtils.base64decode(maybeBase64policy.get))

    val conditions = (policyJson \ "conditions")(0)

    conditions(0).asOpt[String] shouldBe Some("content-length-range")

    block(conditions(1).asOpt[Long], conditions(2).asOpt[Long])
