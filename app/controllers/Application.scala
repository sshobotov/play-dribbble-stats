package controllers

import aggregators.StatsAggregator
import models.JsonProtocol
import play.api.mvc._
import play.api.libs.json.Json
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits._

class Application extends Controller {
  import JsonProtocol._

  def index(login: Option[String]) = Action.async {
    login match {
      case None => Future.successful {
        BadRequest(Json.obj("message" -> "Provide `login` query parameter"))
      }
      case Some(username) => StatsAggregator.aggregate(username) map { data =>
        Ok(Json.toJson(data.toStream))
      }
    }
  }

}
