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

package services

import java.io.File
import java.nio.file.Files

import javax.inject.Singleton
import model.FileId
import org.apache.commons.io.FileUtils
import play.api.Logger
import play.api.libs.{Files => PlayFiles}

case class StoredFile(body: Array[Byte])

@Singleton
class FileStorageService {

  private val logger = Logger(this.getClass)
  private val tempDirectory: File = Files.createTempDirectory("upscan").toFile

  logger.debug(s"Storing files to temporary directory: [${tempDirectory}].")

  def store(temporaryFile: PlayFiles.TemporaryFile): FileId = {
    val fileId = FileId.generate()
    temporaryFile.moveFileTo(buildFileLocation(fileId))
    fileId
  }

  def get(fileId: FileId): Option[StoredFile] =
    for { file <- Some(buildFileLocation(fileId)) if file.exists() && file.isFile && file.canRead } yield
      StoredFile(FileUtils.readFileToByteArray(file))

  private def buildFileLocation(reference: FileId) = new File(tempDirectory, reference.value)
}
