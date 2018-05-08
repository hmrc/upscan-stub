package services

import java.io.File

import play.api.libs.Files

class FileStorageService {
  def store(temporaryFile: Files.TemporaryFile, reference: String): String = {
    temporaryFile.moveTo(new File(s"/resources/uploaded/$reference"))
    reference
  }


}
