import akka.actor.ActorSystem
import javax.inject.{Inject, Provider, Singleton}
import play.api.{Configuration, Environment}
import play.api.inject.{Binding, Module}
import services.{HttpNotificationSender, NotificationQueueProcessor, NotificationSender}

class UpscanStubModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = Seq(
    bind[NotificationSender].to[HttpNotificationSender],
    bind[NotificationQueueProcessor].toProvider[NotificationQueueProcessorProvider]
  )

}

@Singleton
class NotificationQueueProcessorProvider @Inject()(notificationService: NotificationSender)(actorSystem: ActorSystem)
    extends Provider[NotificationQueueProcessor] {
  override def get(): NotificationQueueProcessor =
    new NotificationQueueProcessor(notificationService)(actorSystem)
}
