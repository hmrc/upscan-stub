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

package controllers

import java.net.URL

import filters.UserAgentFilter
import javax.inject.Inject
import model.initiate._
import play.api.Logger
import play.api.libs.json.{JsValue, Json, _}
import play.api.mvc.{Action, ControllerComponents, Result}
import services.PrepareUploadService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class InitiateController @Inject()(prepareUploadService: PrepareUploadService, cc: ControllerComponents)

  extends BackendController(cc)
  with UserAgentFilter {

  private val logger = Logger(this.getClass)

  private implicit val prepareUploadRequestV1Reads: Reads[PrepareUploadRequestV1] =
    PrepareUploadRequestV1.reads(PrepareUploadService.maxFileSize)

  private implicit val prepareUploadRequestV2Reads: Reads[PrepareUploadRequestV2] =
    PrepareUploadRequestV2.reads(PrepareUploadService.maxFileSize)

  def prepareUploadV1(): Action[JsValue] = prepareUpload[PrepareUploadRequestV1]()

  def prepareUploadV2(): Action[JsValue] = prepareUpload[PrepareUploadRequestV2]()

  private def prepareUpload[T <: PrepareUpload]()(implicit reads: Reads[T], manifest: Manifest[T]): Action[JsValue] =
    withUserAgentHeader(logger, cc.actionBuilder) {
      Action.async(parse.json) { implicit request =>
        withJsonBody[T] { prepareUpload: T =>
          withAllowedCallbackProtocol(prepareUpload.callbackUrl) {
            val consumingService = request.headers.get(USER_AGENT)
            logger.debug(s"Received initiate request: [$prepareUpload] from [$consumingService].")
            val url = prepareUpload match {
              case _: PrepareUploadRequestV1 => routes.UploadController.upload().absoluteURL
              case _: PrepareUploadRequestV2 => routes.UploadProxyController.upload().absoluteURL
            }
            val result = prepareUploadService.prepareUpload(prepareUpload.toUploadSettings(url), consumingService)
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
