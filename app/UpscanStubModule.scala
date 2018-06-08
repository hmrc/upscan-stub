import java.time.Clock
import javax.inject.{Inject, Provider, Singleton}

import akka.actor.ActorSystem
import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment}
import services.{HttpNotificationSender, NotificationQueueProcessor, NotificationSender}

class UpscanStubModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = Seq(
    bind[NotificationSender].to[HttpNotificationSender],
    bind[NotificationQueueProcessor].toProvider[NotificationQueueProcessorProvider].in[Singleton],
    bind(classOf[Clock]).toInstance(Clock.systemDefaultZone)
  )

}

class NotificationQueueProcessorProvider @Inject()(notificationService: NotificationSender)(actorSystem: ActorSystem)
    extends Provider[NotificationQueueProcessor] {
  override def get(): NotificationQueueProcessor =
    new NotificationQueueProcessor(notificationService)(actorSystem)
}
