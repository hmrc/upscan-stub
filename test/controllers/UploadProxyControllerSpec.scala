/*
 * Copyright 2022 HM Revenue & Customs
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

package controllers

import akka.util.Timeout
import controllers.UploadProxyController.ErrorAction
import controllers.UploadProxyController.ErrorResponseHandler.proxyErrorResponse
import org.apache.http.client.utils.URIBuilder
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.Helpers.contentAsJson
import play.mvc.Http.HeaderNames.{CONTENT_TYPE, LOCATION}
import play.mvc.Http.MimeTypes.JSON
import play.mvc.Http.Status.{BAD_REQUEST, SEE_OTHER}

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.duration._

class UploadProxyControllerSpec extends AnyWordSpec with Matchers with OptionValues {

  import UploadProxyControllerSpec._

  private implicit val timeout: Timeout = Timeout(1.second)

  "UploadProxyController's ErrorResponseHandler" should {
    "redirect providing error details as query parameters when a redirectUrl is specified" in {
      val errorAction = ErrorAction(redirectUrl = Some("https://myservice.com/error"), Key)

      val result  = proxyErrorResponse(errorAction, statusCode = BAD_REQUEST, AwsXmlResponseBody, AwsResponseHeaders)

      result.header.status shouldBe SEE_OTHER
      val locationHeader = locationHeaderUriFrom(result).value
      locationHeader.getScheme shouldBe "https"
      locationHeader.getHost shouldBe "myservice.com"
      locationHeader.getPath shouldBe "/error"
      queryParametersOf(locationHeader) should contain theSameElementsAs Seq(
        "key" -> Key,
        "errorCode" -> "NoSuchKey",
        "errorMessage" -> "The resource you requested does not exist",
        "errorResource" -> "/mybucket/myfoto.jpg",
        "errorRequestId" -> "4442587FB7D0A2F9"
      )
      result.body.isKnownEmpty shouldBe true
      result.header.headers should not contain key (CONTENT_TYPE)
    }

    "return error details as a json body when a redirectUrl is not specified" in {
      val errorAction = ErrorAction(redirectUrl = None, Key)

      val result = proxyErrorResponse(errorAction, statusCode = BAD_REQUEST, AwsXmlResponseBody, AwsResponseHeaders)

      result.header.status shouldBe BAD_REQUEST
      result.body.contentType should contain (JSON)
      contentAsJson(Future.successful(result)) shouldBe AwsErrorAsJson
    }
  }
}

private object UploadProxyControllerSpec {
  val Key = "ABC123"
  val AwsXmlResponseBody =
    """|<?xml version="1.0" encoding="UTF-8"?>
       |<Error>
       |  <Code>NoSuchKey</Code>
       |  <Message>The resource you requested does not exist</Message>
       |  <Resource>/mybucket/myfoto.jpg</Resource>
       |  <RequestId>4442587FB7D0A2F9</RequestId>
       |</Error>""".stripMargin

  val AwsErrorAsJson = Json.parse(
    s"""|{
        | "key": "$Key",
        | "errorCode": "NoSuchKey",
        | "errorMessage": "The resource you requested does not exist",
        | "errorResource": "/mybucket/myfoto.jpg",
        | "errorRequestId": "4442587FB7D0A2F9"
        |}""".stripMargin)

  val AwsResponseHeaders = Map.empty[String, Seq[String]]

  def locationHeaderUriFrom(result: Result): Option[URIBuilder] =
    result.header.headers.get(LOCATION).map(new URIBuilder(_))

  def queryParametersOf(uri: URIBuilder): Seq[(String, String)] =
    uri.getQueryParams.asScala.map(nv => nv.getName -> nv.getValue)
}
