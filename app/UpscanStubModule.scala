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
