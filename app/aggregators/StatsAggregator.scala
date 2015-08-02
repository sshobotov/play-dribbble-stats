package aggregators

import java.util.{Date, TimerTask, Timer}

import play.api.libs.ws._
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import models._
import play.api.libs.json.Reads
import scala.concurrent.{Promise, Future}
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

  private[this] def request[T](apiResource: String)(implicit reader: Reads[T]): Future[T] = {
    WS.url(serviceConfig.getString("endpoint").get + apiResource)
      .withQueryString("access_token" -> serviceConfig.getString("clientAccessToken").get)
      .get() flatMap { response =>
        response.status match {
          case 200 => Try { response.json.as[T] } match {
            case Success(results) => Future.successful(results)
            case Failure(e)       => Future.failed(e)
          }
          case 429 => response.header("X-RateLimit-Reset").map { _.toLong * 1000 } match {
            case None             => responseError(response)
            case Some(timestamp)  => delay(timestamp - new Date().getTime) {
              request[T](apiResource)
            }
          }
          case _   => responseError(response)
        }
      }
  }

  private[this] def responseError(response: WSResponse) =
    Try { (response.json \ "message").as[String] } match {
      case Success(message) =>
        Future.failed (new Exception(s"Service error: [${response.status}] $message"))
      case Failure(e)       => Future.failed(e)
    }

  private[this] def delay[T](delay: Long)(action: => Future[T]) = {
    val promise = Promise[T]()

    new Timer().schedule(new TimerTask {
      override def run() {
        promise.completeWith(action)
      }
    }, delay)

    promise.future
  }

}
