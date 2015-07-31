package models

import play.api.libs.json._

object JsonProtocol {

  implicit val userWrites = Json.writes[User]
  implicit val userReads = Json.reads[User]

  implicit val followerWrites = Json.writes[Follower]
  implicit val followerReads = Json.reads[Follower]

  implicit val shotWrites = Json.writes[Shot]
  implicit val shotReads = Json.reads[Shot]

  implicit val likeWrites = Json.writes[Like]
  implicit val likeReads = Json.reads[Like]

  implicit val statsWrites = Writes[(User, Int)] { entry =>
    Json.obj(
      "user" -> entry._1,
      "cnt"  -> entry._2
    )
  }

}