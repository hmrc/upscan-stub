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

package uk.gov.hmrc.upscanstub.controller

import org.apache.pekko.util.ByteString
import play.api.Logger
import play.api.http.HttpEntity
import play.api.mvc.{Action, AnyContent, ControllerComponents, ResponseHeader, Result}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.upscanstub.model.FileId
import uk.gov.hmrc.upscanstub.service.FileStorageService

import java.security.MessageDigest
import javax.inject.Inject

class DownloadController @Inject()(
  storageService: FileStorageService,
  cc            : ControllerComponents
) extends BackendController(cc):

  private val logger = Logger(this.getClass)

  def download(fileId: String): Action[AnyContent] =
    Action:
      val result: Result =
        (for
           source <- storageService.get(FileId(fileId))
         yield
           val md    = MessageDigest.getInstance("MD5")
           val bytes = source.body
           md.update(bytes)

           val etag  = md.digest().map("%02x".format(_)).mkString

           Result(
             header = ResponseHeader(200, Map("ETag" -> s""""$etag"""")),
             body   = HttpEntity.Strict(ByteString(bytes), None)
           )
        ).getOrElse(NotFound)

      logger.debug(s"Download request for Key=[$fileId], returning status: [${result.header.status}].")

      result
