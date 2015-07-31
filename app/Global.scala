import play.api.GlobalSettings
import play.api.libs.json.Json
import play.api.mvc.RequestHeader
import play.api.mvc.Results.InternalServerError

import scala.concurrent.Future

object Global extends GlobalSettings {

  override def onError(request: RequestHeader, ex: Throwable) = {
    Future.successful(InternalServerError(Json.obj("message" -> ex.getMessage)))
  }

}