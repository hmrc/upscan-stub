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

package uk.gov.hmrc.upscanstub

import org.apache.pekko.actor.ActorSystem
import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.upscanstub.service.{HttpNotificationSender, NotificationQueueProcessor, NotificationSender}

import java.time.Clock
import javax.inject.{Inject, Provider, Singleton}

class UpscanStubModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = Seq(
    bind[NotificationSender].to[HttpNotificationSender],
    bind[NotificationQueueProcessor].toProvider[NotificationQueueProcessorProvider].in[Singleton],
    bind[Clock].toInstance(Clock.systemDefaultZone)
  )

}

class NotificationQueueProcessorProvider @Inject()(notificationService: NotificationSender)(actorSystem: ActorSystem)
    extends Provider[NotificationQueueProcessor] {
  override def get(): NotificationQueueProcessor =
    new NotificationQueueProcessor(notificationService)(actorSystem)
}
