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

package uk.gov.hmrc.upscanstub.model

import java.net.URL
import java.time.Instant
import java.util.UUID

import play.api.libs.json._

//Common

case class Reference(value: String)

case class FileId(value: String)

object FileId:
  def generate() =
    FileId(UUID.randomUUID().toString)

object Reference:
  given Format[Reference] =
    new Format[Reference]:

      override def writes(reference: Reference): JsValue =
        JsString(reference.value)

      override def reads(json: JsValue): JsResult[Reference] =
        json.validate[String] match
          case JsSuccess(reference, _) => JsSuccess(Reference(reference))
          case error: JsError          => error

//Upload request

case class UploadPostForm(
  algorithm           : String,
  credential          : String,
  date                : String,
  policy              : String,
  signature           : String,
  acl                 : String,
  key                 : String,
  callbackUrl         : String,
  redirectAfterSuccess: Option[String],
  redirectAfterError  : Option[String] = None
)

//Internal model of uploaded file

case class UploadDetails(
  uploadTimestamp: Instant,
  checksum       : String,
  fileMimeType   : String,
  fileName       : String,
  size           : Long
)

object UploadDetails:
  given Writes[UploadDetails] =
    Json.writes[UploadDetails]

enum ProcessedFile(
  val reference: Reference
):

  case UploadedFile(
    override val reference : Reference,
    callbackUrl            : URL,
    downloadUrl            : URL,
    uploadDetails          : UploadDetails
  ) extends ProcessedFile(reference)

  case QuarantinedFile(
    override val reference  : Reference,
    callbackUrl             : URL,
    error                   : String
  ) extends ProcessedFile(reference)

  case RejectedFile(
    override val reference : Reference,
    callbackUrl            : URL,
    error                  : String
  ) extends ProcessedFile(reference)

  case UnknownReasonFile(
    override val reference : Reference,
    callbackUrl            : URL,
    error                  : String
  ) extends ProcessedFile(reference)

case class AWSError(
  code     : String,
  message  : String,
  requestId: String
)

enum ForcedTestFileError:
  case Quarantine(message: String) extends ForcedTestFileError
  case Rejected  (message: String) extends ForcedTestFileError
  case Unknown   (message: String) extends ForcedTestFileError


// Parse a json String representing an AWS policy, and extract the min/max values for the content-length-range condition.
// This is assumed to be the first condition present in the array.
case class ContentLengthRange(
  min: Option[Long],
  max: Option[Long]
)

object ContentLengthRange:

  def extract(policy: String): Option[ContentLengthRange] =
    val json: JsValue = Json.parse(policy)

    (json \ "conditions").asOpt[Seq[Seq[JsValue]]]
      .flatMap:
        case Seq(Seq(JsString("content-length-range"), mi, mx), _*) => Some(ContentLengthRange(mi.asOpt[Long], mx.asOpt[Long]))
        case _                                                      => None
