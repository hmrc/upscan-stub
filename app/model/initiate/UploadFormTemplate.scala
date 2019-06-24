package model.initiate

import play.api.libs.json.{Json, OFormat}

case class UploadFormTemplate(href: String, fields: Map[String, String])

object UploadFormTemplate {

  implicit val writes: OFormat[UploadFormTemplate] = Json.format[UploadFormTemplate]

}
