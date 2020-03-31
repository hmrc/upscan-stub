package controllers

import java.nio.file.Files

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.Files.SingletonTemporaryFileCreator
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import services.FileStorageService

import scala.concurrent.duration._

class DownloadControllerISpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with GivenWhenThen {

  implicit val actorSystem: ActorSystem        = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  "DownloadController" should {

    "download a file" in {
      Given("a reference to a previously stored file")
      val file = SingletonTemporaryFileCreator.create("my-it-file", ".txt")

      Files.write(file.toPath, "Integration test file contents".getBytes)

      val storageService = app.injector.instanceOf[FileStorageService]
      val fileId         = storageService.store(file)

      val downloadRequest = FakeRequest(Helpers.GET, s"/upscan/download/${fileId.value}")

      When("a GET request is made to /download/:reference endpoint")
      val downloadResponse = route(app, downloadRequest).get

      Then("the uploaded file is successfully retrieved")
      status(downloadResponse) shouldBe 200
      val downloadContents: String = contentAsString(downloadResponse)
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
