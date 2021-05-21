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

import model._
import model.initiate.{PrepareUploadResponse, UploadFormTemplate, UploadSettings}
import org.apache.http.client.utils.URIBuilder
import play.api.libs.json.{JsArray, JsObject, Json}

import java.net.URISyntaxException
import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneOffset}
import java.util.UUID.randomUUID
import javax.inject.Inject

class PrepareUploadService @Inject()() {
  private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

  case class Policy(json: JsObject) {
    import utils.Implicits.Base64StringOps
    def asBase64String(): String = Json.stringify(json).base64encode()
  }

  def prepareUpload(settings: UploadSettings, consumingService: Option[String]): PrepareUploadResponse = {
    val reference = generateReference()
    val policy    = toPolicy(settings)

    val now = Instant.now
    PrepareUploadResponse(
      reference = reference,
      uploadRequest = UploadFormTemplate(
        href = settings.uploadUrl,
        fields = Map(
          "acl"                                 -> "private",
          "key"                                 -> reference.value,
          "policy"                              -> policy.asBase64String,
          "x-amz-algorithm"                     -> "AWS4-HMAC-SHA256",
          "x-amz-credential"                    -> "ASIAxxxxxxxxx/20180202/eu-west-2/s3/aws4_request",
          "x-amz-date"                          -> dateTimeFormatter.format(now),
          "x-amz-signature"                     -> "xxxx",
          "x-amz-meta-upscan-initiate-received" -> now.toString,
          "x-amz-meta-upscan-initiate-response" -> now.toString,
          "x-amz-meta-callback-url"             -> settings.callbackUrl,
          "x-amz-meta-original-filename"        -> s"$${filename}",
          "x-amz-meta-session-id"               -> randomUUID().toString,
          "x-amz-meta-request-id"               -> randomUUID().toString
        ) ++ settings.expectedContentType.map { "Content-Type" -> _ }
          ++ settings.successRedirect.map("success_action_redirect" -> successRedirectWithReference(_, reference))
          ++ settings.errorRedirect.map("error_action_redirect"     -> _)
          ++ consumingService.map { "x-amz-meta-consuming-service" -> _ }
      )
    )
  }

  private def successRedirectWithReference(successRedirect: String, reference: Reference): String =
    try {
      val builder = new URIBuilder(successRedirect)
      builder.addParameter("key", reference.value)
      builder.build().toASCIIString
    } catch {
      // retain existing behaviour and continue with an unadulterated (but invalid) successRedirect URL
      case _: URISyntaxException => successRedirect
    }

  private def generateReference(): Reference = Reference(randomUUID().toString)

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
