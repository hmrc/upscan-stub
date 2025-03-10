/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.upscanstub.controller

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.testkit.NoMaterializer
import org.mockito.Mockito
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.upscanstub.model.FileId
import uk.gov.hmrc.upscanstub.service.{FileStorageService, StoredFile}

import scala.concurrent.Future

class DownloadControllerSpec
   extends AnyWordSpec
      with Matchers
      with GivenWhenThen
      with MockitoSugar:

  given ActorSystem = ActorSystem()

  "DownloadController" should:
    "retrieve file from storage if available" in:
      Given("a valid file reference")
      val validFileId = "123-efg-789-0"
      val storedFile  = Some(StoredFile("Here is some file contents".getBytes))

      val storageService = mock[FileStorageService]
      Mockito.when(storageService.get(FileId(validFileId))).thenReturn(storedFile)

      val controller = DownloadController(storageService, stubControllerComponents())

      When("download is called")
      val downloadResult: Future[Result] = controller.download(validFileId)(FakeRequest())

      Then("a successful response should be returned")
      val downloadStatus = status(downloadResult)
      downloadStatus shouldBe 200

      And("the body should be set to the expected file contents")
      val downloadBody: String =  contentAsString(downloadResult)
      downloadBody shouldBe "Here is some file contents"

    "return Not Found if file not available" in:
      Given("an valid file reference")
      val validFileId = "123-efg-789-0"
      val storedFile  = None

      val storageService = mock[FileStorageService]
      Mockito.when(storageService.get(FileId(validFileId))).thenReturn(storedFile)

      val controller = DownloadController(storageService, stubControllerComponents())

      When("download is called")
      val downloadResult: Future[Result] = controller.download(validFileId)(FakeRequest())

      Then("a Not Found response should be returned")
      val downloadStatus = status(downloadResult)
      downloadStatus shouldBe 404
