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

import org.apache.pekko.actor.ActorSystem
import play.api.{Configuration, Logger}
import play.api.libs.json._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}
import uk.gov.hmrc.upscanstub.model._

import java.net.URL
import javax.inject.Inject
import scala.concurrent.duration.{DurationLong, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future, Promise}

trait NotificationSender:
  def sendNotification(uploadedFile: ProcessedFile): Future[Unit]

case class ReadyCallbackBody(
  reference    : Reference,
  downloadUrl  : URL,
  fileStatus   : FileStatus   = FileStatus.Ready,
  uploadDetails: UploadDetails
)

object ReadyCallbackBody:
  private given Writes[URL] =
    (o: URL) => JsString(o.toString)
  given Writes[ReadyCallbackBody] =
    Json.writes[ReadyCallbackBody]

case class FailedCallbackBody(
  reference     : Reference,
  fileStatus    : FileStatus = FileStatus.Failed,
  failureDetails: ErrorDetails
)

object FailedCallbackBody:
  given Writes[FailedCallbackBody] =
    Json.writes[FailedCallbackBody]

enum FileStatus(val status: String):
  case Ready  extends FileStatus("READY")
  case Failed extends FileStatus("FAILED")

object FileStatus:
  given Writes[FileStatus] =
    (o: FileStatus) => JsString(o.status)

case class ErrorDetails(
  failureReason: String,
  message      : String
)

object ErrorDetails:
  given Format[ErrorDetails] =
    Json.format[ErrorDetails]

class HttpNotificationSender @Inject()(
  httpClient : HttpClient,
  actorSystem: ActorSystem,
  config     : Configuration,
)(using
  ExecutionContext
) extends NotificationSender:

  import uk.gov.hmrc.http.HttpReads.Implicits._

  private val logger = Logger(this.getClass)

  private given HeaderCarrier = HeaderCarrier()

  private val artificialDelay =
    config.getMillis("notifications.artificial-delay").millis // TODO configure duration

  override def sendNotification(uploadedFile: ProcessedFile): Future[Unit] =
    withArtificialDelay(artificialDelay):
      uploadedFile match
        case f: ProcessedFile.UploadedFile      => notifySuccessfulCallback(f)
        case f: ProcessedFile.QuarantinedFile   => notifyFailedCallback(f.reference, "QUARANTINE", f.error, f.callbackUrl)
        case f: ProcessedFile.RejectedFile      => notifyFailedCallback(f.reference, "REJECTED", f.error, f.callbackUrl)
        case f: ProcessedFile.UnknownReasonFile => notifyFailedCallback(f.reference, "UNKNOWN", f.error, f.callbackUrl)

  private def notifySuccessfulCallback(uploadedFile: ProcessedFile.UploadedFile): Future[Unit] =
    val callback =
      ReadyCallbackBody(uploadedFile.reference, uploadedFile.downloadUrl, uploadDetails = uploadedFile.uploadDetails)

    httpClient
      .POST[ReadyCallbackBody, HttpResponse](uploadedFile.callbackUrl, callback)
      .map: httpResponse =>
        logger.info:
          s"""File ready notification for Key=[${uploadedFile.reference.value}] sent to service with callbackUrl: [${uploadedFile.callbackUrl}].
             |Notification=[$callback].  Response status was: [${httpResponse.status}].""".stripMargin

  private def notifyFailedCallback(
    reference    : Reference,
    failureReason: String,
    error        : String,
    callbackUrl  : URL
  ): Future[Unit] =
    val callback =
      FailedCallbackBody(reference, failureDetails = ErrorDetails(failureReason, error))

    httpClient
      .POST[FailedCallbackBody, HttpResponse](callbackUrl, callback)
      .map: httpResponse =>
        logger.info(
          s"""File failed notification for Key=[${reference.value}] sent to service with callbackUrl: [${callbackUrl}].
             |Notification=[$callback].  Response status was: [${httpResponse.status}].""".stripMargin
        )

  private def withArtificialDelay[A](delay: FiniteDuration)(action: => Future[A]): Future[A] =
    val p = Promise[Unit]()
    actorSystem.scheduler.scheduleOnce(delay)(p.success(()))
    p.future.flatMap(_ => action)
