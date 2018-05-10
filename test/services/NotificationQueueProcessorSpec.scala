package services

import java.net.URL
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import model.{FileData, Reference}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import uk.gov.hmrc.play.test.UnitSpec

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.concurrent.duration._

class NotificationQueueProcessorSpec extends UnitSpec with BeforeAndAfterAll with Eventually {

  implicit val actorSystem: ActorSystem = ActorSystem("test")

  override implicit val patienceConfig = PatienceConfig(
    timeout  = scaled(5 seconds),
    interval = scaled(100 millis)
  )

  override def afterAll {
    actorSystem.terminate()
  }

  class NotificationSenderStub(val expectedFailures: Int) extends NotificationSender {

    val callCounter = new AtomicInteger(0)

    val successfulNotifications = TrieMap[Reference, FileData]()

    override def sendNotification(uploadedFile: FileData): Future[Unit] =
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
        FileData(
          new URL("http://127.0.0.1/callback"),
          Reference("REF1"),
          new URL("http://127.0.0.1/download"),
          1L,
          Some(Instant.now()))
      val file2 =
        FileData(
          new URL("http://127.0.0.1/callback"),
          Reference("REF2"),
          new URL("http://127.0.0.1/download"),
          1L,
          Some(Instant.now()))
      val file3 =
        FileData(
          new URL("http://127.0.0.1/callback"),
          Reference("REF3"),
          new URL("http://127.0.0.1/download"),
          1L,
          Some(Instant.now()))

      processor.enqueueNotification(file1)
      processor.enqueueNotification(file2)
      processor.enqueueNotification(file3)

      eventually {
        notificationService.successfulNotifications.size shouldBe 3
      }

      notificationService.successfulNotifications.values should contain allOf (file1, file2, file3)

    }

    "retry notification if failed" in {
      val notificationService = new NotificationSenderStub(expectedFailures = 2)

      val processor =
        new NotificationQueueProcessor(notificationService, retryDelay = 30 milliseconds, maximumRetryCount = 2)

      val file =
        FileData(new URL("http://callback"), Reference("REF1"), new URL("http://download"), 1L, Some(Instant.now()))

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
        FileData(new URL("http://callback"), Reference("REF1"), new URL("http://download"), 1L, Some(Instant.now()))

      processor.enqueueNotification(file)

      Thread.sleep(1000)

      notificationService.successfulNotifications shouldBe empty
    }
  }

}
