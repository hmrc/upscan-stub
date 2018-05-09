package models

import java.net.URL
import java.time.Instant

import play.api.libs.json._

case class UploadSettings(
  callbackUrl: String,
  minimumFileSize: Option[Int],
  maximumFileSize: Option[Int],
  expectedContentType: Option[String])

object UploadSettings {
  implicit val settingsFormat: Format[UploadSettings] = Json.format[UploadSettings]
}

case class UploadFormTemplate(href: String, fields: Map[String, String])

object UploadFormTemplate {
  implicit val templateFormat: Format[UploadFormTemplate] = Json.format[UploadFormTemplate]
}

case class Reference(value: String)

case class UploadedFile(
  callbackUrl: URL,
  reference: Reference,
  downloadUrl: URL,
  size: Long,
  uploadTimestamp: Option[Instant])

object Reference {
  implicit val referenceFormat: Format[Reference] = new Format[Reference] {

    override def writes(reference: Reference): JsValue = JsString(reference.value)

    override def reads(json: JsValue): JsResult[Reference] = json.validate[String] match {
      case JsSuccess(reference, _) => JsSuccess(Reference(reference))
      case error: JsError          => error
    }
  }
}

case class ContentLengthRange(min: Int, max: Int)

object ContentLengthRange {
  implicit val contentLengthFormat: Format[ContentLengthRange] = Json.format[ContentLengthRange]
}

case class UploadParameters(
  expirationDateTime: Instant,
  bucketName: String,
  objectKey: String,
  acl: String,
  additionalMetadata: Map[String, String],
  contentLengthRange: ContentLengthRange,
  expectedContentType: Option[String]
)

object UploadParameters {
  implicit val parametersFormat: Format[UploadParameters] = Json.format[UploadParameters]

}

case class PreparedUpload(reference: Reference, uploadRequest: UploadFormTemplate)

object PreparedUpload {
  implicit val uploadFormat: Format[PreparedUpload] = Json.format[PreparedUpload]
}
