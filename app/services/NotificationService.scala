package services

import java.net.URL

import javax.inject.Inject
import models.{Reference, UploadedFile}
import play.api.Logger
import play.api.libs.json._
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import models.JsonWriteHelpers.urlFormats

import scala.concurrent.{ExecutionContext, Future}

trait NotificationService {
  def sendNotification(uploadedFile: UploadedFile): Future[Unit]
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

class HttpNotificationService @Inject()(httpClient: HttpClient)(implicit ec: ExecutionContext)
    extends NotificationService {

  override def sendNotification(uploadedFile: UploadedFile): Future[Unit] = {

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

}
