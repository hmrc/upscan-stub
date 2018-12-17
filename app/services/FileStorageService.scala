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

  private val tempDirectory: File = Files.createTempDirectory("upscan").toFile

  Logger.debug(s"Storing files to temporary directory: [${tempDirectory}].")

  def store(temporaryFile: PlayFiles.TemporaryFile): FileId = {
    val fileId = FileId.generate()
    temporaryFile.moveTo(buildFileLocation(fileId))
    fileId
  }

  def get(fileId: FileId): Option[StoredFile] =
    for { file <- Some(buildFileLocation(fileId)) if file.exists() && file.isFile && file.canRead } yield
      StoredFile(FileUtils.readFileToByteArray(file))

  private def buildFileLocation(reference: FileId) = new File(tempDirectory, reference.value)
}
