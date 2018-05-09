package services

import java.io.File
import java.nio.file.Files

import javax.inject.Singleton
import models.Reference
import org.apache.commons.io.FileUtils
import play.api.libs.{Files => PlayFiles}

case class StoredFile(body: Array[Byte])

@Singleton
class FileStorageService {

  private val tempDirectory: File = Files.createTempDirectory("upscan").toFile

  def store(temporaryFile: PlayFiles.TemporaryFile, reference: Reference): Unit =
    temporaryFile.moveTo(buildFileLocation(reference))

  def get(reference: Reference): Option[StoredFile] =
    for { file <- Some(buildFileLocation(reference)) if file.exists() && file.isFile && file.canRead } yield
      StoredFile(FileUtils.readFileToByteArray(file))

  private def buildFileLocation(reference: Reference) = new File(tempDirectory, reference.value)
}
