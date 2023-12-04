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

package services

import org.apache.pekko.actor.{Actor, ActorLogging, ActorSystem, Props}
import org.apache.pekko.pattern.pipe
import org.apache.pekko.util.Timeout
import model.ProcessedFile

import scala.collection.immutable.Queue
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class NotificationQueueProcessor(
  notificationService: NotificationSender,
  maximumRetryCount: Int     = 10,
  retryDelay: FiniteDuration = 30.seconds
)(implicit actorSystem: ActorSystem) {

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

class QueueProcessingActor(
  notificationSender: NotificationSender,
  maximumRetryCount : Int,
  retryDelay        : FiniteDuration
) extends Actor with ActorLogging {

  implicit val timeout: Timeout = Timeout(5.seconds)

  implicit val ec: ExecutionContext = context.system.dispatcher

  private var running = false

  private var queue = Queue[Notification]()

  override def receive: Receive = {
    case EnqueueNotification(file) =>
      queue = queue.enqueue(file)
      processQueue()
    case NotificationProcessedSuccessfully(notification) =>
      log.info(s"Notification for Key=[${notification.fileData.reference.value}] sent successfully [$notification]")
      running = false
      processQueue()
    case NotificationProcessingFailed(error, notification) =>
      running = false
      processQueue()
      if (notification.retryCount < maximumRetryCount) {
        log.warning(s"Sending notification for Key=[${notification.fileData.reference.value}] failed [$notification]. Retrying", error)
        context.system.scheduler.scheduleOnce(retryDelay, self, EnqueueNotification(notification.retried))
      } else {
        log.warning(s"Sending notification for Key=[${notification.fileData.reference.value}] failed [$notification]. Retry limit reached", error)
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
