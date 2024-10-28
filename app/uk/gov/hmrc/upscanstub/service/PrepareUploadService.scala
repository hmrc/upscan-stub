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

package uk.gov.hmrc.upscanstub.service

import org.apache.http.client.utils.URIBuilder
import play.api.libs.json.{JsArray, JsObject, Json}
import uk.gov.hmrc.upscanstub.model._
import uk.gov.hmrc.upscanstub.model.initiate.{PrepareUploadResponse, UploadFormTemplate, UploadSettings}
import uk.gov.hmrc.upscanstub.util.Base64StringUtils

import java.net.URISyntaxException
import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneOffset}
import java.util.UUID.randomUUID
import javax.inject.Inject

class PrepareUploadService @Inject()():
  private val dateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

  case class Policy(json: JsObject):

    def asBase64String(): String =
      Base64StringUtils.base64encode(Json.stringify(json))

  def prepareUpload(settings: UploadSettings): PrepareUploadResponse =
    val reference = generateReference()
    val policy    = toPolicy(settings)

    val now = Instant.now
    PrepareUploadResponse(
      reference     = reference,
      uploadRequest = UploadFormTemplate(
        href   = settings.uploadUrl,
        fields = Map(
          "acl"                                 -> "private",
          "key"                                 -> reference.value,
          "policy"                              -> policy.asBase64String(),
          "x-amz-algorithm"                     -> "AWS4-HMAC-SHA256",
          "x-amz-credential"                    -> "ASIAxxxxxxxxx/20180202/eu-west-2/s3/aws4_request",
          "x-amz-date"                          -> dateTimeFormatter.format(now),
          "x-amz-signature"                     -> "xxxx",
          "x-amz-meta-upscan-initiate-received" -> now.toString,
          "x-amz-meta-upscan-initiate-response" -> now.toString,
          "x-amz-meta-callback-url"             -> settings.prepareUploadRequest.callbackUrl,
          "x-amz-meta-original-filename"        -> s"$${filename}",
          "x-amz-meta-session-id"               -> randomUUID().toString,
          "x-amz-meta-request-id"               -> randomUUID().toString,
          "x-amz-meta-consuming-service"        -> settings.consumingService
        ) ++ settings.prepareUploadRequest.successRedirect.map("success_action_redirect" -> successRedirectWithReference(_, reference))
          ++ settings.prepareUploadRequest.errorRedirect.map("error_action_redirect"     -> _)
      )
    )

  private def successRedirectWithReference(successRedirect: String, reference: Reference): String =
    try
      val builder = URIBuilder(successRedirect)
      builder.addParameter("key", reference.value)
      builder.build().toASCIIString
    catch
      // retain existing behaviour and continue with an unadulterated (but invalid) successRedirect URL
      case _: URISyntaxException => successRedirect

  private def generateReference(): Reference =
    Reference(randomUUID().toString)

  private def toPolicy(settings: UploadSettings): Policy =
    Policy:
      Json.obj(
        "conditions" -> JsArray(
          Seq(
            Json.arr(
              "content-length-range",
              settings.prepareUploadRequest.minimumFileSize.foldLeft(PrepareUploadService.minFileSize)(math.max),
              settings.prepareUploadRequest.maximumFileSize.foldLeft(PrepareUploadService.maxFileSize)(math.min)
            )
          )
        )
      )

object PrepareUploadService:
  val minFileSize = 0L
  val maxFileSize = 104857600L
