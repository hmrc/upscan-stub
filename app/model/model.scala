package model

import java.net.URL
import java.time.Instant

import play.api.libs.json._

//Common

case class Reference(value: String)

object Reference {
  implicit val referenceFormat: Format[Reference] = new Format[Reference] {

    override def writes(reference: Reference): JsValue = JsString(reference.value)

    override def reads(json: JsValue): JsResult[Reference] = json.validate[String] match {
      case JsSuccess(reference, _) => JsSuccess(Reference(reference))
      case error: JsError          => error
    }
  }
}

//Initiate request

case class UploadSettings(
  callbackUrl: String,
  minimumFileSize: Option[Int],
  maximumFileSize: Option[Int],
  expectedContentType: Option[String])

object UploadSettings {
  implicit val settingsFormat: Format[UploadSettings] = Json.format[UploadSettings]
}

//Initiate response

case class UploadFormTemplate(href: String, fields: Map[String, String])

object UploadFormTemplate {
  implicit val templateFormat: Format[UploadFormTemplate] = Json.format[UploadFormTemplate]
}

case class PreparedUpload(reference: Reference, uploadRequest: UploadFormTemplate)

object PreparedUpload {
  implicit val uploadFormat: Format[PreparedUpload] = Json.format[PreparedUpload]
}

//Upload request

case class UploadPostForm(
  algorithm: String,
  credential: String,
  date: String,
  policy: String,
  signature: String,
  acl: String,
  key: String,
  callbackUrl: String
)

//Internal model of uploaded file

case class FileData(
  callbackUrl: URL,
  reference: Reference,
  downloadUrl: URL,
  size: Long,
  uploadTimestamp: Option[Instant])
