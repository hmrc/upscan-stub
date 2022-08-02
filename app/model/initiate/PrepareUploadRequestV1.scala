/*
 * Copyright 2021 HM Revenue & Customs
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

package model.initiate

import play.api.libs.functional.syntax._
import play.api.libs.json.Reads.{max, min}
import play.api.libs.json.{JsPath, JsonValidationError, Reads}

case class PrepareUploadRequestV1(
  callbackUrl: String,
  minimumFileSize: Option[Int],
  maximumFileSize: Option[Int],
  successRedirect: Option[String])
    extends PrepareUpload {

  def toUploadSettings(uploadUrl: String): UploadSettings = UploadSettings(
    uploadUrl           = uploadUrl,
    callbackUrl         = callbackUrl,
    minimumFileSize     = minimumFileSize,
    maximumFileSize     = maximumFileSize,
    successRedirect     = successRedirect,
    errorRedirect       = None
  )

}

object PrepareUploadRequestV1 {

  def reads(maxFileSize: Int): Reads[PrepareUploadRequestV1] =
    ((JsPath \ "callbackUrl").read[String] and
      (JsPath \ "minimumFileSize").readNullable[Int](min(0)) and
      (JsPath \ "maximumFileSize").readNullable[Int](min(0) keepAnd max(maxFileSize + 1)) and
      (JsPath \ "successRedirect").readNullable[String])(PrepareUploadRequestV1.apply _)
      .filter(JsonValidationError("Maximum file size must be equal or greater than minimum file size"))(request =>
        request.minimumFileSize.getOrElse(0) <= request.maximumFileSize.getOrElse(maxFileSize))

}
