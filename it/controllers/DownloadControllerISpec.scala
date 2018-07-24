package controllers

import java.io.File
import java.nio.file.{Files, Paths}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import model.{FileId, Reference}
import org.scalatest.GivenWhenThen
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.Files.TemporaryFile
import play.api.test.Helpers.{route, _}
import play.api.test.{FakeRequest, Helpers}
import services.FileStorageService
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.duration._

class DownloadControllerISpec extends UnitSpec with GuiceOneAppPerSuite with GivenWhenThen {

  implicit val actorSystem: ActorSystem        = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val timeout: akka.util.Timeout      = 10.seconds

  "DownloadController" should {

    "download a file" in {
      Given("a reference to a previously stored file")
      val file: File = Files.createTempFile(Paths.get("/tmp"), "my-it-file", ".txt").toFile
      file.deleteOnExit
      Files.write(file.toPath, "Integration test file contents".getBytes)

      val storageService = app.injector.instanceOf[FileStorageService]
      val fileId         = storageService.store(new TemporaryFile(file))

      val downloadRequest = FakeRequest(Helpers.GET, s"/upscan/download/${fileId.value}")

      When("a GET request is made to /download/:reference endpoint")
      val downloadResponse = route(app, downloadRequest).get

      Then("the uploaded file is successfully retrieved")
      status(downloadResponse) shouldBe 200
      val downloadContents: String = bodyOf(downloadResponse)
      downloadContents shouldBe "Integration test file contents"

      And("the file is no longer in its original location")
      Files.exists(file.toPath) shouldBe false
    }

    "return Not Found for invalid file reference" in {
      Given("an invalid file reference")
      val downloadRequest = FakeRequest(Helpers.GET, "/upscan/download/my-invalid-file")

      When("a GET request is made to /download/:reference endpoint")
      val downloadResponse = route(app, downloadRequest).get

      Then("a Not Found response should be returned")
      status(downloadResponse) shouldBe 404
    }
  }
}
