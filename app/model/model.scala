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

sealed trait ProcessedFile {
  def reference: Reference
}

case class UploadDetails(uploadTimestamp: Instant, checksum: String, fileMimeType: String, fileName: String)

object UploadDetails {
  implicit val writesUploadDetails: Writes[UploadDetails] = Json.writes[UploadDetails]
}

case class UploadedFile(callbackUrl: URL, reference: Reference, downloadUrl: URL, uploadDetails: UploadDetails)
    extends ProcessedFile

case class QuarantinedFile(callbackUrl: URL, reference: Reference, error: String) extends ProcessedFile

case class AWSError(code: String, message: String, requestId: String)

// Parse a json String representing an AWS policy, and extract the min/max values for the content-length-range condition.
// This is assumed to be the first condition present in the array.
case class ContentLengthRange(min: Option[Long], max: Option[Long])

object ContentLengthRange {
  def extract(policy: String): Option[ContentLengthRange] = {
    val json: JsValue = Json.parse(policy)

    val conditions = (json \ "conditions")(0)

    val contentLengthRangeMaybe = conditions(0).asOpt[String]

    for {
      clr <- contentLengthRangeMaybe
      if clr == "content-length-range"
      minMaybe = conditions(1).asOpt[Long]
      maxMaybe = conditions(2).asOpt[Long]
    } yield ContentLengthRange(minMaybe, maxMaybe)
  }
}
