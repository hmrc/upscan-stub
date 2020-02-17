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

package filters

import play.api.Logger
import play.api.http.HeaderNames
import play.api.mvc.Action
import play.api.mvc.Results.Forbidden

import scala.concurrent.Future

trait UserAgentFilter {

  def withUserAgentHeader[A](action: Action[A]): Action[A] = Action.async(action.parser) { request =>
    if (request.headers.get(HeaderNames.USER_AGENT).isDefined) {
      action(request)
    } else {
      Logger.warn("Missing User-Agent header.")

      Future.successful(Forbidden("This service is not allowed to use upscan-initiate. " +
        "If you need to use this service, please contact Platform Services team."))
    }
  }
}