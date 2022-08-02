/*
 * Copyright 2021 HM Revenue & Customs
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

package services

import model.initiate.{PrepareUploadResponse, UploadSettings}
import org.apache.http.client.utils.URIBuilder
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsValue, Json}
import utils.Implicits._

class PrepareUploadServiceSpec extends AnyWordSpec with Matchers with OptionValues {
  "PrepareUploadService.prepareUpload" should {
    val testInstance = new PrepareUploadService()
    val userAgent    = Some("PrepareUploadServiceSpec")

    val uploadSettings = UploadSettings(
      uploadUrl           = "uploadUrl",
      callbackUrl         = "callbackUrl",
      minimumFileSize     = None,
      maximumFileSize     = None,
      successRedirect     = None,
      errorRedirect       = None
    )

    "include supplied file size constraints in the policy" in {
      val result = testInstance.prepareUpload(uploadSettings.copy(minimumFileSize = Some(10), maximumFileSize = Some(100)),
        userAgent)

      withMinMaxFileSizesInPolicyConditions(result) { (min, max) =>
        min shouldBe Some(10)
        max shouldBe Some(100)
      }
    }

    "include default file size constraints in the policy" in {
      val result = testInstance.prepareUpload(uploadSettings.copy(minimumFileSize = None, maximumFileSize = None),
        userAgent)

      withMinMaxFileSizesInPolicyConditions(result) { (min, max) =>
        min shouldBe Some(0)
        max shouldBe Some(104857600)
      }
    }

    "include all required fields" in {
      val result = testInstance.prepareUpload(uploadSettings, userAgent)

      val formFields = result.uploadRequest.fields

      formFields.get("x-amz-meta-original-filename").value shouldBe s"$${filename}"

      formFields.keySet should contain theSameElementsAs Set(
        "acl",
        "key",
        "policy",
        "x-amz-algorithm",
        "x-amz-credential",
        "x-amz-date",
        "x-amz-meta-callback-url",
        "x-amz-meta-consuming-service",
        "x-amz-signature",
        "x-amz-meta-upscan-initiate-response",
        "x-amz-meta-upscan-initiate-received",
        "x-amz-meta-session-id",
        "x-amz-meta-request-id",
        "x-amz-meta-original-filename"
      )
    }

    "include upload redirect URLs when specified" in {
      val result = testInstance.prepareUpload(uploadSettings.copy(
        errorRedirect = Some("https://www.example.com/error"),
        successRedirect = Some("https://www.example.com/success")
      ), userAgent)

      result.uploadRequest.fields.get("error_action_redirect") should contain ("https://www.example.com/error")
      result.uploadRequest.fields.get("success_action_redirect").value should startWith("https://www.example.com/success?key=")
    }

    "retain any existing query parameters on upload redirect URLs when specified" in {
      val result = testInstance.prepareUpload(uploadSettings.copy(
        errorRedirect = Some("https://www.example.com/error?upload=1234"),
        successRedirect = Some("https://www.example.com/success?upload=1234")
      ), userAgent)

      import scala.collection.JavaConverters._
      val successActionRedirectUrl = result.uploadRequest.fields.get("success_action_redirect").map(new URIBuilder(_)).value
      val successActionQueryParams = successActionRedirectUrl.getQueryParams.asScala

      result.uploadRequest.fields.get("error_action_redirect") should contain ("https://www.example.com/error?upload=1234")

      successActionQueryParams.map(_.getName) should contain theSameElementsAs Seq("upload", "key")
      successActionQueryParams.exists(qp => qp.getName == "upload" && qp.getValue == "1234") shouldBe true
      successActionRedirectUrl.clearParameters().build().toASCIIString shouldBe "https://www.example.com/success"
    }
  }

  private def withMinMaxFileSizesInPolicyConditions[T](preparedUpload: PrepareUploadResponse)(
    block: (Option[Long], Option[Long]) => T): T = {

    val policy: String = preparedUpload.uploadRequest.fields("policy").base64decode()

    val policyJson: JsValue = Json.parse(policy)

    val conditions = (policyJson \ "conditions")(0)

    conditions(0).asOpt[String] shouldBe Some("content-length-range")

    block(conditions(1).asOpt[Long], conditions(2).asOpt[Long])
  }
}
