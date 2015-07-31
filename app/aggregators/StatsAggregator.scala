package aggregators

import play.api.libs.ws._
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import models._
import play.api.libs.json.Reads
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

object StatsAggregator {
  import JsonProtocol._

  def aggregate(username: String) = {
    request[List[Follower]](s"/users/$username/followers") flatMap { followers =>
      Future.sequence {
        followers.filter(_.follower.shots_count > 0) map { entry =>
          request[List[Shot]](s"/users/${entry.follower.username}/shots")
        }
      }
    } flatMap { shots =>
      Future.sequence {
        shots.flatten.filter(_.likes_count > 0) map { entry =>
          request[List[Like]](s"/shots/${entry.id}/likes")
        }
      }
    } map { likes =>
      likes.flatten.groupBy(_.user.id) mapValues { userLikes =>
        (userLikes.head.user, userLikes.length)
      }
    } map { _.values.toList.sortBy(- _._2) }
  }

  private[this] val serviceConfig = current.configuration.getConfig("api.dribbble").get

  private[this] def request[T](apiResource: String)(implicit reader: Reads[T]) = {
    WS.url(serviceConfig.getString("endpoint").get + apiResource)
      .withQueryString("access_token" -> serviceConfig.getString("clientAccessToken").get)
      .get() flatMap { response =>
        response.status match {
          case 200 => Try { response.json.as[T] } match {
            case Success(results) => Future.successful(results)
            case Failure(e)       => Future.failed(e)
          }
          case _   => Try { (response.json \ "message").as[String] } match {
            case Success(message) =>
              Future.failed (new Exception(s"Service error: [${response.status}] $message") )
            case Failure(e)       => Future.failed(e)
          }
        }
      }
  }

}
