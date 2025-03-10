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

package uk.gov.hmrc.upscanstub.service

import org.apache.pekko.actor.ActorSystem
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.upscanstub.connector.NotificationSender
import uk.gov.hmrc.upscanstub.model.{ProcessedFile, Reference, UploadDetails}

import java.net.URL
import java.time.Instant
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import scala.concurrent.Future
import scala.concurrent.duration._

class NotificationQueueProcessorSpec
  extends AnyWordSpec
     with Matchers
     with BeforeAndAfterAll
     with Eventually
     with IntegrationPatience:

  given actorSystem: ActorSystem = ActorSystem("test")

  override def afterAll(): Unit =
    actorSystem.terminate()

  private val initiateDate = Instant.parse("2018-04-24T09:30:00Z")

  class NotificationSenderStub(val expectedFailures: Int) extends NotificationSender:

    val callCounter = AtomicInteger(0)

    val successfulNotifications = AtomicReference(Seq[(Reference, ProcessedFile)]())

    override def sendNotification(uploadedFile: ProcessedFile): Future[Unit] =
      if callCounter.incrementAndGet() > expectedFailures then
        successfulNotifications.updateAndGet(_ :+ (uploadedFile.reference -> uploadedFile))
        Future.successful(())
      else
        Future.failed(Exception("Expected exception"))

  "NotificationQueueProcessor" should:
    "process notifications in the order of enqueueing" in:

      val notificationService = NotificationSenderStub(0)

      val processor = NotificationQueueProcessor(notificationService)

      val file1 =
        ProcessedFile.UploadedFile(
          Reference("REF1"),
          URL("http://127.0.0.1/callback"),
          URL("http://127.0.0.1/download"),
          UploadDetails(initiateDate, checksum = "12345", "application/pdf", "test.pdf", size = 123L)
        )

      val file2 =
        ProcessedFile.UploadedFile(
          Reference("REF2"),
          URL("http://127.0.0.1/callback"),
          URL("http://127.0.0.1/download"),
          UploadDetails(initiateDate, checksum = "12345", "application/pdf", "test.pdf", size = 456L)
        )

      val file3 =
        ProcessedFile.UploadedFile(
          Reference("REF3"),
          URL("http://127.0.0.1/callback"),
          URL("http://127.0.0.1/download"),
          UploadDetails(initiateDate, checksum = "12345", "application/pdf", "test.pdf", size = 789L)
        )

      processor.enqueueNotification(file1)
      processor.enqueueNotification(file2)
      processor.enqueueNotification(file3)

      eventually:
        notificationService.successfulNotifications.get.size shouldBe 3

      notificationService.successfulNotifications.get.map(_._2) should contain theSameElementsInOrderAs Seq(file1, file2, file3)

    "retry notification if failed" in:
      val notificationService = NotificationSenderStub(expectedFailures = 2)

      val processor =
        NotificationQueueProcessor(notificationService, retryDelay = 30.milliseconds, maximumRetryCount = 2)

      val file =
        ProcessedFile.UploadedFile(
          Reference("REF1"),
          URL("http://callback"),
          URL("http://download"),
          UploadDetails(initiateDate, checksum = "12345", "application/pdf", "test.pdf", size = 123L)
        )

      processor.enqueueNotification(file)

      eventually:
        notificationService.successfulNotifications.get.map(_._2) should contain(file)

    "fail permanently after certain amount of retries" in:
      val notificationService = NotificationSenderStub(expectedFailures = 3)

      val processor =
        NotificationQueueProcessor(notificationService, retryDelay = 100.milliseconds, maximumRetryCount = 2)

      val file =
        ProcessedFile.UploadedFile(
          Reference("REF1"),
          URL("http://callback"),
          URL("http://download"),
          UploadDetails(initiateDate, checksum = "12345", "applicaiton/pdf", "test.pdf", size = 123L)
        )

      processor.enqueueNotification(file)

      Thread.sleep(1000)

      notificationService.successfulNotifications.get shouldBe empty
