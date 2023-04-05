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

package controllers

import java.net.URL
import filters.UserAgentFilter

import javax.inject.Inject
import model.initiate._
import play.api.Logger
import play.api.libs.json.{JsValue, Json, _}
import play.api.mvc.{Action, Call, ControllerComponents, Result}
import services.PrepareUploadService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class InitiateController @Inject()(prepareUploadService: PrepareUploadService, cc: ControllerComponents)
    extends BackendController(cc)
    with UserAgentFilter {

  private val logger = Logger(this.getClass)

  private val prepareUploadRequestReadsV1: Reads[PrepareUploadRequest] =
    PrepareUploadRequest.readsV1(PrepareUploadService.maxFileSize)

  def prepareUploadV1(): Action[JsValue] =
    prepareUpload(routes.UploadController.upload)(prepareUploadRequestReadsV1)

  private val prepareUploadRequestReadsV2: Reads[PrepareUploadRequest] =
    PrepareUploadRequest.readsV2(PrepareUploadService.maxFileSize)

  def prepareUploadV2(): Action[JsValue] =
    prepareUpload(routes.UploadProxyController.upload)(prepareUploadRequestReadsV2)

  private def prepareUpload(uploadCall: Call)(implicit reads: Reads[PrepareUploadRequest]): Action[JsValue] =
    withUserAgentHeader(logger, cc.actionBuilder) {
      Action.async(parse.json) { implicit request =>
        withJsonBody[PrepareUploadRequest] { prepareUploadRequest =>
          withAllowedCallbackProtocol(prepareUploadRequest.callbackUrl) {
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
          }
        }
      }
    }

  private[controllers] def withAllowedCallbackProtocol[A](callbackUrl: String)(
    block: => Future[Result]): Future[Result] = {

    val isHttps: Try[Boolean] = Try {
      val url = new URL(callbackUrl)
      url.getProtocol == "https" || callbackUrl.startsWith("http://localhost")
    }

    isHttps match {
      case Success(true) => block
      case Success(false) => {
        logger.warn(s"Invalid callback url protocol: [$callbackUrl].")

        Future.successful(BadRequest(s"Invalid callback url protocol: [$callbackUrl]. Protocol must be https."))
      }
      case Failure(e) => {
        logger.warn(s"Invalid callback url format: [$callbackUrl].")

        Future.successful(BadRequest(s"Invalid callback url format: [$callbackUrl]. [${e.getMessage}]"))
      }
    }

  }
}
