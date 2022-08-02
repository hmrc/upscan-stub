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

package model.initiate

import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json.Json

class PrepareUploadRequestV2Spec extends AnyWordSpecLike with Matchers with EitherValues {

  import PrepareUploadRequestV2Spec._

  "A v2 upload request" when {
    "specifying an upload success redirect URL" should {
      "parse as a valid request containing a success redirect URL" in {
        val request = s"""|{"callbackUrl":"$CallbackUrl",
                          | "successRedirect":"$SuccessRedirectUrl"}""".stripMargin
        val parseResult = Json.parse(request).validate(PrepareUploadRequestV2.reads(MaxFileSize))

        parseResult.asEither.map(_.successRedirect).right.value should contain (SuccessRedirectUrl)
      }

      "generate upload settings that contain the success redirect URL" in {
        val uploadSettings = aV2RequestWithSuccessRedirectUrlOf(Some(SuccessRedirectUrl)).toUploadSettings(UploadUrl)

        uploadSettings.successRedirect should contain (SuccessRedirectUrl)
      }
    }

    "omitting an upload success redirect URL" should {
      "parse as a valid request without a success redirect URL" in {
        val request = s"""{"callbackUrl":"$CallbackUrl"}"""
        val parseResult = Json.parse(request).validate(PrepareUploadRequestV2.reads(MaxFileSize))

        parseResult.asEither.map(_.successRedirect).right.value shouldBe empty
      }

      "generate upload settings without a success redirect URL" in {
        val uploadSettings = aV2RequestWithSuccessRedirectUrlOf(None).toUploadSettings(UploadUrl)

        uploadSettings.successRedirect shouldBe empty
      }
    }

    "specifying an upload error redirect URL" should {
      "parse as a valid request containing an error redirect URL" in {
        val request = s"""|{"callbackUrl":"$CallbackUrl",
                          | "errorRedirect":"$ErrorRedirectUrl"}""".stripMargin
        val parseResult = Json.parse(request).validate(PrepareUploadRequestV2.reads(MaxFileSize))

        parseResult.asEither.map(_.errorRedirect).right.value should contain (ErrorRedirectUrl)
      }

      "generate upload settings that contain the error redirect URL" in {
        val uploadSettings = aV2RequestWithErrorRedirectUrlOf(Some(ErrorRedirectUrl)).toUploadSettings(UploadUrl)

        uploadSettings.errorRedirect should contain (ErrorRedirectUrl)
      }
    }

    "omitting an upload error redirect URL" should {
      "parse as a valid request without an error redirect URL" in {
        val request = s"""{"callbackUrl":"$CallbackUrl"}"""
        val parseResult = Json.parse(request).validate(PrepareUploadRequestV2.reads(MaxFileSize))

        parseResult.asEither.map(_.errorRedirect).right.value shouldBe empty
      }

      "generate upload settings without an error redirect URL" in {
        val uploadSettings = aV2RequestWithErrorRedirectUrlOf(None).toUploadSettings(UploadUrl)

        uploadSettings.errorRedirect shouldBe empty
      }
    }
  }
}

private object PrepareUploadRequestV2Spec {
  val UploadUrl = "https://xxxx/upscan-upload-proxy/bucketName"
  val CallbackUrl = "https://myservice.com/callback"
  val SuccessRedirectUrl = "https://myservice.com/nextPage"
  val ErrorRedirectUrl = "https://myservice.com/errorPage"
  val MaxFileSize = 512

  private val template = PrepareUploadRequestV2(
    callbackUrl = CallbackUrl,
    successRedirect = None,
    errorRedirect = None,
    minimumFileSize = None,
    maximumFileSize = None)

  def aV2RequestWithSuccessRedirectUrlOf(url: Option[String]): PrepareUploadRequestV2 =
    template.copy(successRedirect = url)

  def aV2RequestWithErrorRedirectUrlOf(url: Option[String]): PrepareUploadRequestV2 =
    template.copy(errorRedirect = url)
}
