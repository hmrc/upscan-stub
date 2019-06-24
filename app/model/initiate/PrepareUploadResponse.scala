package model.initiate

import model.Reference
import play.api.libs.json._

case class PrepareUploadResponse(reference: Reference, uploadRequest: UploadFormTemplate)

object PrepareUploadResponse {

  implicit val format: OFormat[PrepareUploadResponse] = Json.format[PrepareUploadResponse]

}
