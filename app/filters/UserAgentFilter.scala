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

package filters

import play.api.Logger
import play.api.http.HeaderNames.USER_AGENT
import play.api.mvc.Results.BadRequest
import play.api.mvc.{Action, ActionBuilder, AnyContent, Request}

import scala.concurrent.Future

trait UserAgentFilter {

  def withUserAgentHeader[A](logger: Logger, actionBuilder: ActionBuilder[Request, AnyContent])
                            (action: Action[A]): Action[A] = actionBuilder.async(action.parser) { request =>
    if (request.headers.get(USER_AGENT).isDefined) {
      action(request)
    } else {
      logger.warn(s"Missing $USER_AGENT Header.")
      Future.successful(BadRequest(s"Missing $USER_AGENT Header"))
    }
  }
}