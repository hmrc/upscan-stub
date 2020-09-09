/*
 * Copyright 2020 HM Revenue & Customs
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

package controllers

import akka.util.ByteString
import javax.inject.Inject
import model.FileId
import play.api.Logger
import play.api.http.HttpEntity
import play.api.mvc.{ControllerComponents, ResponseHeader, Result}
import services.FileStorageService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

class DownloadController @Inject()(storageService: FileStorageService, cc: ControllerComponents) extends BackendController(cc) {

  private val logger = Logger(this.getClass)

  def download(fileId: String) = Action {

    val result: Result = (for {
      source <- storageService.get(FileId(fileId))
    } yield {
      Result(
        header = ResponseHeader(200, Map.empty),
        body   = HttpEntity.Strict(ByteString(source.body), None)
      )
    }) getOrElse NotFound

    logger.debug(s"Download request for file reference: [${fileId}], returning status: [${result.header.status}].")

    result
  }
}
