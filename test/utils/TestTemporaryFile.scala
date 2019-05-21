package utils

import java.io.{FileOutputStream, InputStream}

import org.apache.commons.io.IOUtils
import play.api.libs.Files.{SingletonTemporaryFileCreator, TemporaryFile}

object TestTemporaryFile {

  def apply(path : String) = {

    val testResource: InputStream = getClass.getResourceAsStream(path)
    assert(testResource != null, s"Resource $path not found")
    val tempFile = SingletonTemporaryFileCreator.create("file", "tmp")
    IOUtils.copy(testResource, new FileOutputStream(tempFile))
    new TemporaryFile(tempFile)
  }

}
