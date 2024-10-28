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

import play.api.Logger
import play.api.libs.json.{JsValue, Json, _}
import play.api.mvc.{Action, Call, ControllerComponents, Request, Result}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.upscanstub.filter.UserAgentFilter
import uk.gov.hmrc.upscanstub.model.initiate._
import uk.gov.hmrc.upscanstub.service.PrepareUploadService

import java.net.URL
import javax.inject.Inject
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class InitiateController @Inject()(
  prepareUploadService: PrepareUploadService,
  cc                  : ControllerComponents
) extends BackendController(cc)
     with UserAgentFilter:

  private val logger = Logger(this.getClass)

  // because of cyclical dependency between routes and controllers, this must be lazy
  // for the correct reverse route url
  lazy val prepareUploadV1: Action[JsValue] =
    given Reads[PrepareUploadRequest] = PrepareUploadRequest.readsV1(PrepareUploadService.maxFileSize)
    prepareUpload(routes.UploadController.upload)

  lazy val prepareUploadV2: Action[JsValue] =
    given Reads[PrepareUploadRequest] = PrepareUploadRequest.readsV2(PrepareUploadService.maxFileSize)
    prepareUpload(routes.UploadProxyController.upload)

  private def prepareUpload(uploadCall: Call)(using Reads[PrepareUploadRequest]): Action[JsValue] =
    withUserAgentHeader(logger, cc.actionBuilder):
      Action.async(parse.json): request =>
        given Request[JsValue] = request
        withJsonBody[PrepareUploadRequest]: prepareUploadRequest =>
          withAllowedCallbackProtocol(prepareUploadRequest.callbackUrl):
            val userAgent = request.headers.get(USER_AGENT).get
            logger.debug(s"Received initiate request: [$prepareUploadRequest] from [$userAgent].")

            val settings =
              UploadSettings(
                uploadUrl            = uploadCall.absoluteURL(),
                userAgent            = userAgent,
                prepareUploadRequest = prepareUploadRequest
              )

            val result = prepareUploadService.prepareUpload(settings)
            logger.debug(s"Prepared initiate upload response with Key=[${result.reference.value}]")
            Future.successful(Ok(Json.toJson(result)(PrepareUploadResponse.format)))

  private[controller] def withAllowedCallbackProtocol[A](
    callbackUrl: String
  )(
    block: => Future[Result]
  ): Future[Result] =
    val isHttps: Try[Boolean] =
      Try:
        URL(callbackUrl).getProtocol == "https"
          || callbackUrl.startsWith("http://localhost")

    isHttps match
      case Success(true)  => block
      case Success(false) => logger.warn(s"Invalid callback url protocol: [$callbackUrl].")
                             Future.successful(BadRequest(s"Invalid callback url protocol: [$callbackUrl]. Protocol must be https."))
      case Failure(e)     => logger.warn(s"Invalid callback url format: [$callbackUrl].")
                             Future.successful(BadRequest(s"Invalid callback url format: [$callbackUrl]. [${e.getMessage}]"))

end InitiateController
