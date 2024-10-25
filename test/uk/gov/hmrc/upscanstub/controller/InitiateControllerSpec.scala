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

import org.apache.pekko.stream._
import org.apache.pekko.stream.testkit.NoMaterializer
import org.mockito.ArgumentMatchers.argThat
import org.mockito.Mockito.when
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.Action
import play.api.mvc.Results.Ok
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.upscanstub.model.Reference
import uk.gov.hmrc.upscanstub.model.initiate.{PrepareUploadResponse, UploadFormTemplate, UploadSettings}
import uk.gov.hmrc.upscanstub.service.PrepareUploadService

import scala.concurrent.Future

class InitiateControllerSpec
  extends AnyWordSpec
     with Matchers
     with GivenWhenThen
     with MockitoSugar:

  import InitiateControllerSpec._

  private implicit val materializer: Materializer = NoMaterializer

  private val requestHeaders = (USER_AGENT, UserAgent)

  "Upscan Initiate V1 with only mandatory form fields" should:
    val uploadSettingsMatcher: UploadSettings => Boolean =
      settings =>
        settings.uploadUrl.endsWith("/upload") &&
        settings.userAgent == UserAgent &&
        settings.prepareUploadRequest.callbackUrl == CallbackUrl &&
        settings.prepareUploadRequest.minimumFileSize.isEmpty &&
        settings.prepareUploadRequest.maximumFileSize.isEmpty &&
        settings.prepareUploadRequest.successRedirect.isEmpty &&
        settings.prepareUploadRequest.errorRedirect.isEmpty &&
        settings.prepareUploadRequest.consumingService.isEmpty &&
        settings.consumingService == UserAgent

    behave like upscanInitiateTests(_.prepareUploadV1(), uploadSettingsMatcher)

  "Upscan Initiate V1 with all form fields" should:
    val optionalFields = Json.obj(
      "minimumFileSize"     -> 0,
      "maximumFileSize"     -> 1024,
      "expectedContentType" -> XML,
      "successRedirect"     -> SuccessRedirectUrl,
      "consumingService"    -> ConsumingService
    )

    val uploadSettingsMatcher: UploadSettings => Boolean =
      settings =>
        settings.uploadUrl.endsWith("/upload") &&
        settings.userAgent == UserAgent &&
        settings.prepareUploadRequest.callbackUrl == CallbackUrl &&
        settings.prepareUploadRequest.minimumFileSize.contains(0L) &&
        settings.prepareUploadRequest.maximumFileSize.contains(1024L) &&
        settings.prepareUploadRequest.successRedirect.contains(SuccessRedirectUrl) &&
        settings.prepareUploadRequest.errorRedirect.isEmpty &&
        settings.prepareUploadRequest.consumingService.contains(ConsumingService) &&
        settings.consumingService == ConsumingService

    behave like upscanInitiateTests(_.prepareUploadV1(), uploadSettingsMatcher, optionalFields)

  "Upscan Initiate V2 with only mandatory form fields" should:
    val uploadSettingsMatcher: UploadSettings => Boolean =
      settings =>
        settings.uploadUrl.endsWith("/upload-proxy") &&
        settings.userAgent == UserAgent &&
        settings.prepareUploadRequest.callbackUrl == CallbackUrl &&
        settings.prepareUploadRequest.minimumFileSize.isEmpty &&
        settings.prepareUploadRequest.maximumFileSize.isEmpty &&
        settings.prepareUploadRequest.successRedirect.isEmpty &&
        settings.prepareUploadRequest.errorRedirect.isEmpty &&
        settings.prepareUploadRequest.consumingService.isEmpty &&
        settings.consumingService == UserAgent

    behave like upscanInitiateTests(_.prepareUploadV2(), uploadSettingsMatcher)

  "Upscan Initiate V2 with all form fields" should:
    val extraRequestFields = Json.obj(
      "errorRedirect"       -> ErrorRedirectUrl,
      "minimumFileSize"     -> 0,
      "maximumFileSize"     -> 1024,
      "expectedContentType" -> XML,
      "successRedirect"     -> SuccessRedirectUrl,
      "consumingService"    -> ConsumingService
    )
    val uploadSettingsMatcher: UploadSettings => Boolean =
      settings =>
        settings.uploadUrl.endsWith("/upload-proxy") &&
        settings.userAgent == UserAgent &&
        settings.prepareUploadRequest.callbackUrl == CallbackUrl &&
        settings.prepareUploadRequest.minimumFileSize.contains(0L) &&
        settings.prepareUploadRequest.maximumFileSize.contains(1024L) &&
        settings.prepareUploadRequest.successRedirect.contains(SuccessRedirectUrl) &&
        settings.prepareUploadRequest.errorRedirect.contains(ErrorRedirectUrl) &&
        settings.prepareUploadRequest.consumingService.contains(ConsumingService) &&
        settings.consumingService == ConsumingService

    behave like upscanInitiateTests(_.prepareUploadV2(), uploadSettingsMatcher, extraRequestFields)

  private def upscanInitiateTests(
    // scalastyle:ignore
    route                : InitiateController => Action[JsValue],
    uploadSettingsMatcher: UploadSettings => Boolean,
    extraRequestFields   : JsObject = JsObject.empty
  ): Unit =
    "return expected JSON for prepare upload when passed valid request" in:
      Given("a request containing a valid JSON body")
      val validJsonBody = Json.obj("callbackUrl" -> CallbackUrl) ++ extraRequestFields
      val request = FakeRequest().withHeaders(requestHeaders).withBody(validJsonBody)

      When("the prepare upload method is called")
      val preparedUpload = PrepareUploadResponse(
        Reference("abcd-efgh-1234"),
        UploadFormTemplate(UploadUrl, Map.empty)
      )
      val prepareService = mock[PrepareUploadService]
      when(prepareService.prepareUpload(argThat(uploadSettings => uploadSettingsMatcher(uploadSettings))))
        .thenReturn(preparedUpload)

      val controller = new InitiateController(prepareService, stubControllerComponents())
      val result = route(controller)(request)

      Then("a successful HTTP response should be returned")
      val responseStatus = status(result)
      responseStatus shouldBe OK

      And("the response should contain the expected JSON body")
      val body = contentAsJson(result)
      body shouldBe Json.obj(
        "reference" -> "abcd-efgh-1234",
        "uploadRequest" -> Json.obj(
          "href" -> UploadUrl,
          "fields" -> Json.obj()
        )
      )

    "return expected error for prepare upload when passed invalid JSON request" in:
      Given("a request containing an invalid JSON body")
      val invalidJsonBody = Json.parse("""
          |{
          |	"someKey": "someValue",
          |	"someOtherKey" : 12345
          |}""".stripMargin)

      val request = FakeRequest().withHeaders(requestHeaders).withBody(invalidJsonBody)

      When("the prepare upload method is called")
      val prepareService = mock[PrepareUploadService]
      val controller = new InitiateController(prepareService, stubControllerComponents())
      val result = route(controller)(request)

      Then("a BadRequest response should be returned")
      status(result) shouldBe BAD_REQUEST

    "return expected error for prepare upload when passed non-JSON request" in:
      Given("a request containing an invalid JSON body")
      val invalidStringBody = "This is an invalid body"
      val request = FakeRequest().withHeaders(requestHeaders).withBody(invalidStringBody)

      When("the prepare upload method is called")
      val prepareService = mock[PrepareUploadService]
      val controller = new InitiateController(prepareService, stubControllerComponents())
      val result = route(controller)(request).run()

      Then("an Unsupported Media Type response should be returned")
      status(result) shouldBe UNSUPPORTED_MEDIA_TYPE

    "allow https callback urls" in:
      val controller = new InitiateController(mock[PrepareUploadService], stubControllerComponents())

      val result =
        controller.withAllowedCallbackProtocol("https://my.callback.url"):
          Future.successful(Ok)

      status(result) shouldBe OK

    "disallow http callback urls" in:
      val controller = new InitiateController(mock[PrepareUploadService], stubControllerComponents())

      val result =
        controller.withAllowedCallbackProtocol("http://my.callback.url"):
          Future.failed(new RuntimeException("This block should not have been invoked."))

      status(result)          shouldBe BAD_REQUEST
      contentAsString(result) should include("Invalid callback url protocol")

    "disallow invalidly formatted callback urls" in:
      val controller = new InitiateController(mock[PrepareUploadService], stubControllerComponents())

      val result =
        controller.withAllowedCallbackProtocol("123"):
          Future.failed(new RuntimeException("This block should not have been invoked."))

      status(result)          shouldBe BAD_REQUEST
      contentAsString(result) should include("Invalid callback url format")

private object InitiateControllerSpec:
  val CallbackUrl        = "https://myservice.com/callback"
  val UserAgent          = "InitiateControllerSpec"
  val ConsumingService   = "some-consuming-service"
  val SuccessRedirectUrl = "https://www.example.com/nextpage"
  val ErrorRedirectUrl   = "https://www.example.com/error"
  val UploadUrl          = "http://myservice.com/upload"
