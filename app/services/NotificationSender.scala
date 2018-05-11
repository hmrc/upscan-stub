package services

import java.net.URL

import javax.inject.Inject
import model._
import play.api.Logger
import play.api.libs.json._
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import model.JsonWriteHelpers.urlFormats

import scala.concurrent.{ExecutionContext, Future}

trait NotificationSender {
  def sendNotification(uploadedFile: ProcessedFile): Future[Unit]
}

case class ReadyCallbackBody(reference: Reference, downloadUrl: URL, fileStatus: FileStatus = ReadyFileStatus)
object ReadyCallbackBody {
  implicit val writesReadyCallback: Writes[ReadyCallbackBody] = new Writes[ReadyCallbackBody] {
    def writes(body: ReadyCallbackBody): JsObject = Json.obj(
      "reference"   -> body.reference.value,
      "downloadUrl" -> body.downloadUrl,
      "fileStatus"  -> body.fileStatus
    )
  }
}

case class FailedCallbackBody(reference: Reference, details: String, fileStatus: FileStatus = FailedFileStatus)
object FailedCallbackBody {
  implicit val writesFailedCallback: Writes[FailedCallbackBody] = new Writes[FailedCallbackBody] {
    def writes(body: FailedCallbackBody): JsObject = Json.obj(
      "reference"  -> body.reference.value,
      "details"    -> body.details,
      "fileStatus" -> body.fileStatus
    )
  }
}

sealed trait FileStatus {
  val status: String
}
case object ReadyFileStatus extends FileStatus {
  override val status: String = "READY"
}
case object FailedFileStatus extends FileStatus {
  override val status: String = "FAILED"
}

object FileStatus {
  implicit val fileStatusWrites: Writes[FileStatus] = new Writes[FileStatus] {
    override def writes(o: FileStatus): JsValue = JsString(o.status)
  }
}

class HttpNotificationSender @Inject()(httpClient: HttpClient)(implicit ec: ExecutionContext)
    extends NotificationSender {

  override def sendNotification(uploadedFile: ProcessedFile): Future[Unit] = uploadedFile match {
    case f: UploadedFile    => notifySuccessfulCallback(f)
    case f: QuarantinedFile => notifyFailedCallback(f)
  }

  private def notifySuccessfulCallback(uploadedFile: UploadedFile): Future[Unit] = {

    implicit val hc: HeaderCarrier = HeaderCarrier()

    val callback = ReadyCallbackBody(uploadedFile.reference, uploadedFile.downloadUrl)

    httpClient
      .POST[ReadyCallbackBody, HttpResponse](uploadedFile.callbackUrl.toString, callback)
      .map { httpResponse =>
        Logger.info(
          s"""File ready notification sent to service with callbackUrl: [${uploadedFile.callbackUrl}].
             | Response status was: [${httpResponse.status}].""".stripMargin
        )
      }
  }

  private def notifyFailedCallback(quarantinedFile: QuarantinedFile): Future[Unit] = {

    implicit val hc: HeaderCarrier = HeaderCarrier()
    val callback                   = FailedCallbackBody(quarantinedFile.reference, quarantinedFile.error)

    httpClient
      .POST[FailedCallbackBody, HttpResponse](quarantinedFile.callbackUrl.toString, callback)
      .map { httpResponse =>
        Logger.info(
          s"""File failed notification sent to service with callbackUrl: [${quarantinedFile.callbackUrl}].
             | Response status was: [${httpResponse.status}].""".stripMargin
        )
      }
  }

}
