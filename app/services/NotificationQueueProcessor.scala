package services

import akka.actor.{Actor, ActorSystem, Props}
import akka.pattern.pipe
import akka.util.Timeout
import model.ProcessedFile
import play.api.Logger

import scala.collection.immutable.Queue
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class NotificationQueueProcessor(
  notificationService: NotificationSender,
  maximumRetryCount: Int     = 10,
  retryDelay: FiniteDuration = 30 seconds)(implicit actorSystem: ActorSystem) {

  private val notificationProcessingActor =
    actorSystem.actorOf(QueueProcessingActor(notificationService, maximumRetryCount, retryDelay))

  def enqueueNotification(uploadedFile: ProcessedFile): Unit =
    notificationProcessingActor ! EnqueueNotification(Notification(uploadedFile))

}

case class EnqueueNotification(notification: Notification)
case class NotificationProcessedSuccessfully(notification: Notification)
case class NotificationProcessingFailed(e: Throwable, notification: Notification)

case class Notification(fileData: ProcessedFile, retryCount: Int = 0) {
  def retried: Notification = this.copy(retryCount = retryCount + 1)
}

object QueueProcessingActor {
  def apply(notificationSender: NotificationSender, maximumRetryCount: Int, retryDelay: FiniteDuration) =
    Props(new QueueProcessingActor(notificationSender, maximumRetryCount, retryDelay))
}

class QueueProcessingActor(notificationSender: NotificationSender, maximumRetryCount: Int, retryDelay: FiniteDuration)
    extends Actor {

  implicit val timeout: Timeout = Timeout(5 seconds)

  implicit val ec: ExecutionContext = context.system.dispatcher

  private var running = false

  private var queue = Queue[Notification]()

  override def receive: Receive = {
    case EnqueueNotification(file) =>
      queue = queue.enqueue(file)
      processQueue()
    case NotificationProcessedSuccessfully(notification) =>
      Logger.info(s"Notification $notification sent successfully")
      running = false
      processQueue()
    case NotificationProcessingFailed(error, notification) =>
      running = false
      processQueue()
      if (notification.retryCount < maximumRetryCount) {
        Logger.warn(s"Sending notification $notification failed. Retrying", error)
        context.system.scheduler.scheduleOnce(retryDelay, self, EnqueueNotification(notification.retried))
      } else {
        Logger.warn(s"Sending notification $notification failed. Retry limit reached", error)
      }
  }

  private def processQueue(): Unit =
    for ((queueTop, newQueue) <- queue.dequeueOption if !running) {
      queue   = newQueue
      running = true
      notificationSender
        .sendNotification(queueTop.fileData)
        .map { _ =>
          NotificationProcessedSuccessfully(queueTop)
        }
        .recover {
          case e: Throwable => NotificationProcessingFailed(e, queueTop)
        }
        .pipeTo(self)
    }

}
