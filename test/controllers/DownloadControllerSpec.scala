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

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import model.{FileId, Reference}
import org.mockito.Mockito
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{GivenWhenThen, Matchers}
import play.api.mvc.Result
import play.api.test.FakeRequest
import services.{FileStorageService, StoredFile}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future
import scala.concurrent.duration._

class DownloadControllerSpec extends UnitSpec with Matchers with GivenWhenThen with MockitoSugar {

  implicit val actorSystem: ActorSystem        = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val timeout: akka.util.Timeout      = 10.seconds

  "DownloadController" should {
    "retrieve file from storage if available" in {
      Given("a valid file reference")
      val validFileId = "123-efg-789-0"
      val storedFile  = Some(StoredFile("Here is some file contents".getBytes))

      val storageService = mock[FileStorageService]
      Mockito.when(storageService.get(FileId(validFileId))).thenReturn(storedFile)

      val controller = new DownloadController(storageService)

      When("download is called")
      val downloadResult: Future[Result] = controller.download(validFileId)(FakeRequest())

      Then("a successful response should be returned")
      val downloadStatus = status(downloadResult)
      downloadStatus shouldBe 200

      And("the body should be set to the expected file contents")
      val downloadBody: String = bodyOf(downloadResult)
      downloadBody shouldBe "Here is some file contents"
    }

    "return Not Found if file not available" in {
      Given("an valid file reference")
      val validFileId = "123-efg-789-0"
      val storedFile  = None

      val storageService = mock[FileStorageService]
      Mockito.when(storageService.get(FileId(validFileId))).thenReturn(storedFile)

      val controller = new DownloadController(storageService)

      When("download is called")
      val downloadResult: Future[Result] = controller.download(validFileId)(FakeRequest())

      Then("a Not Found response should be returned")
      val downloadStatus = status(downloadResult)
      downloadStatus shouldBe 404
    }
  }

}
