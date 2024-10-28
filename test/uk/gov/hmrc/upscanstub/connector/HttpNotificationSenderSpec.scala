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

package uk.gov.hmrc.upscanstub.connector

import org.apache.pekko.actor.ActorSystem
import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import play.api.libs.json.Json
import play.mvc.Http.Status.OK
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.upscanstub.model.{ProcessedFile, Reference, UploadDetails}

import java.time.Instant
import scala.concurrent.ExecutionContext

class HttpNotificationSenderSpec
  extends AnyWordSpec
     with Matchers
     with HttpClientV2Support
     with WireMockSupport
     with ScalaFutures
     with IntegrationPatience:

  import ExecutionContext.Implicits._

  val actorSystem: ActorSystem = ActorSystem()

  val connector = HttpNotificationSender(
    httpClientV2,
    actorSystem,
    config = Configuration("notifications.artificial-delay" -> "10.millis")
  )

  "NotificationSender" should:
    "send UploadedFile" in:
      stubFor:
        post(urlPathEqualTo("/upscan/callback"))
          .willReturn(aResponse().withStatus(OK))

      val uploadDetails = UploadDetails(
        uploadTimestamp = Instant.parse("2021-01-01T00:00:00Z"),
        checksum        = "1234",
        fileMimeType    = "application/pdf",
        fileName        = "my-file.pdf",
        size            = 5678L
      )


      connector.sendNotification(ProcessedFile.UploadedFile(
        reference     = Reference("ref123"),
        callbackUrl   = url"$wireMockUrl/upscan/callback",
        downloadUrl   = url"http://aws/download",
        uploadDetails = uploadDetails
      )).futureValue


      val expectedCallback =
        Json.obj(
          "reference"     -> "ref123",
          "downloadUrl"   -> "http://aws/download",
          "fileStatus"    -> "READY",
          "uploadDetails" -> Json.obj(
            "uploadTimestamp" -> "2021-01-01T00:00:00Z",
            "checksum"        -> "1234",
            "fileMimeType"    -> "application/pdf",
            "fileName"        -> "my-file.pdf",
            "size"            -> 5678
          )
        )
        .toString

      wireMockServer.verify(
        postRequestedFor(urlPathEqualTo("/upscan/callback"))
          .withRequestBody(equalToJson(expectedCallback, true, true))
      )

    "send QuarantinedFile" in:
      stubFor:
        post(urlPathEqualTo("/upscan/callback"))
          .willReturn(aResponse().withStatus(OK))

      connector.sendNotification(ProcessedFile.QuarantinedFile(
        reference     = Reference("ref123"),
        callbackUrl   = url"$wireMockUrl/upscan/callback",
        error         = "The failure"
      )).futureValue


      val expectedCallback =
        Json.obj(
          "reference"     -> "ref123",
          "fileStatus"    -> "FAILED",
          "failureDetails" -> Json.obj(
            "failureReason" -> "QUARANTINE",
            "message"       -> "The failure"
          )
        )
        .toString

      wireMockServer.verify(
        postRequestedFor(urlPathEqualTo("/upscan/callback"))
          .withRequestBody(equalToJson(expectedCallback, true, true))
      )
