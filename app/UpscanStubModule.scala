import play.api.{Configuration, Environment}
import play.api.inject.{Binding, Module}
import services.{HttpNotificationService, NotificationService}

class UpscanStubModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = Seq(
    bind[NotificationService].to[HttpNotificationService]
  )
}
