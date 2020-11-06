/*
 * Copyright 2020 HM Revenue & Customs
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

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneOffset}
import java.util.UUID

import javax.inject.Inject
import model._
import model.initiate.{PrepareUploadResponse, UploadFormTemplate, UploadSettings}
import play.api.libs.json.{JsArray, JsObject, Json}

class PrepareUploadService @Inject()() {
  private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

  case class Policy(json: JsObject) {
    import utils.Implicits.Base64StringOps
    def asBase64String(): String = Json.stringify(json).base64encode()
  }

  def prepareUpload(settings: UploadSettings, consumingService: Option[String]): PrepareUploadResponse = {
    val reference = generateReference()
    val policy    = toPolicy(settings)

    PrepareUploadResponse(
      reference = reference,
      uploadRequest = UploadFormTemplate(
        href = settings.uploadUrl,
        fields = Map(
          "acl"                     -> "private",
          "key"                     -> reference.value,
          "policy"                  -> policy.asBase64String,
          "x-amz-algorithm"         -> "AWS4-HMAC-SHA256",
          "x-amz-credential"        -> "ASIAxxxxxxxxx/20180202/eu-west-2/s3/aws4_request",
          "x-amz-date"              -> dateTimeFormatter.format(Instant.now),
          "x-amz-meta-callback-url" -> settings.callbackUrl,
          "x-amz-signature"         -> "xxxx"
        ) ++ settings.expectedContentType.map { "Content-Type" -> _ }
          ++ settings.successRedirect.map(u => "success_action_redirect" -> s"$u?key=${reference.value}")
          ++ settings.errorRedirect.map("error_action_redirect"     -> _)
          ++ consumingService.map { "x-amz-meta-consuming-service" -> _ }
      )
    )
  }

  private def generateReference(): Reference = Reference(UUID.randomUUID().toString)

  private def toPolicy(settings: UploadSettings): Policy = {
    val json = Json.obj(
      "conditions" -> JsArray(
        Seq(
          Json.arr(
            "content-length-range",
            settings.minimumFileSize.foldLeft(PrepareUploadService.minFileSize)(math.max),
            settings.maximumFileSize.foldLeft(PrepareUploadService.maxFileSize)(math.min)
          )
        )
      )
    )
    Policy(json)
  }
}

object PrepareUploadService {
  val minFileSize = 0
  val maxFileSize = 104857600
}
