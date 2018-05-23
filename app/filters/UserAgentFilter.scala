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