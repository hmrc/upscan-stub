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

import akka.actor.ActorSystem
import model.{ProcessedFile, Reference, UploadDetails, UploadedFile}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.net.URL
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.concurrent.duration._

class NotificationQueueProcessorSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll with Eventually {

  implicit val actorSystem: ActorSystem = ActorSystem("test")

  override implicit val patienceConfig = PatienceConfig(
    timeout  = scaled(5 seconds),
    interval = scaled(100 millis)
  )

  override def afterAll {
    actorSystem.terminate()
  }

  private val initiateDate = Instant.parse("2018-04-24T09:30:00Z")

  class NotificationSenderStub(val expectedFailures: Int) extends NotificationSender {

    val callCounter = new AtomicInteger(0)

    val successfulNotifications = TrieMap[Reference, ProcessedFile]()

    override def sendNotification(uploadedFile: ProcessedFile): Future[Unit] =
      if (callCounter.incrementAndGet() > expectedFailures) {
        successfulNotifications.put(uploadedFile.reference, uploadedFile)
        Future.successful(())
      } else {
        Future.failed(new Exception("Expected exception"))
      }
  }

  "NotificationQueueProcessor" should {
    "process notifications in the order of enqueueing" in {

      val notificationService = new NotificationSenderStub(0)

      val processor = new NotificationQueueProcessor(notificationService)

      val file1 =
        UploadedFile(
          new URL("http://127.0.0.1/callback"),
          Reference("REF1"),
          new URL("http://127.0.0.1/download"),
          UploadDetails(initiateDate, checksum = "12345", "application/pdf", "test.pdf", size = 123L)
        )
      val file2 =
        UploadedFile(
          new URL("http://127.0.0.1/callback"),
          Reference("REF2"),
          new URL("http://127.0.0.1/download"),
          UploadDetails(initiateDate, checksum = "12345", "application/pdf", "test.pdf", size = 456L)
        )
      val file3 =
        UploadedFile(
          new URL("http://127.0.0.1/callback"),
          Reference("REF3"),
          new URL("http://127.0.0.1/download"),
          UploadDetails(initiateDate, checksum = "12345", "application/pdf", "test.pdf", size = 789L)
        )

      processor.enqueueNotification(file1)
      processor.enqueueNotification(file2)
      processor.enqueueNotification(file3)

      eventually {
        notificationService.successfulNotifications.size shouldBe 3
      }

      notificationService.successfulNotifications.values.toSeq should contain theSameElementsInOrderAs Seq(file1, file2, file3)

    }

    "retry notification if failed" in {
      val notificationService = new NotificationSenderStub(expectedFailures = 2)

      val processor =
        new NotificationQueueProcessor(notificationService, retryDelay = 30 milliseconds, maximumRetryCount = 2)

      val file =
        UploadedFile(
          new URL("http://callback"),
          Reference("REF1"),
          new URL("http://download"),
          UploadDetails(initiateDate, checksum = "12345", "application/pdf", "test.pdf", size = 123L)
        )

      processor.enqueueNotification(file)

      eventually {
        notificationService.successfulNotifications.values should contain(file)
      }
    }

    "fail permanently after certain amount of retries" in {
      val notificationService = new NotificationSenderStub(expectedFailures = 3)

      val processor =
        new NotificationQueueProcessor(notificationService, retryDelay = 100 milliseconds, maximumRetryCount = 2)

      val file =
        UploadedFile(
          new URL("http://callback"),
          Reference("REF1"),
          new URL("http://download"),
          UploadDetails(initiateDate, checksum = "12345", "applicaiton/pdf", "test.pdf", size = 123L)
        )

      processor.enqueueNotification(file)

      Thread.sleep(1000)

      notificationService.successfulNotifications shouldBe empty
    }
  }

}
