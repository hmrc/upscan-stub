package services

import java.nio.file.Files
import java.util.UUID

import models.Reference
import org.apache.commons.io.FileUtils
import org.scalatest.{GivenWhenThen, Matchers}
import play.api.libs.Files.TemporaryFile
import uk.gov.hmrc.play.test.UnitSpec

class FileStorageServiceTest extends UnitSpec with Matchers with GivenWhenThen {

  val fileStorageService = new FileStorageService

  "File storage service" should {
    "Allow to store and retrieve file" in {

      val reference = Reference(UUID.randomUUID().toString)

      Given("there is a temporary file")
      val temporaryFile =
        new TemporaryFile(Files.createTempFile("upscan-test", "").toFile)
      val fileBody = "TEST".getBytes
      FileUtils.writeByteArrayToFile(temporaryFile.file, fileBody)

      When("we store content of temporary file to the service")
      fileStorageService.store(temporaryFile, reference)

      Then("we should be able to read this content")
      val retrievedFile = fileStorageService.get(reference)
      retrievedFile          shouldBe defined
      retrievedFile.get.body shouldBe fileBody

    }

    "Return empty result when trying to retrieve non-existent file" in {
      val retrievedFile = fileStorageService.get(Reference("non-existent-file"))
      retrievedFile shouldBe empty
    }

  }
}
