package edu.luc.etl.cs313.scala.clickcounter.service
package repository

import scala.concurrent.ExecutionContext
import model.Counter

/** Requires a running Redis server running locally or at a URL defined as `REDISCLOUD_URL`. */
class RedisRepositorySpec extends RepositorySpec with RedisRepositoryProvider {

  import spray.json._
  import DefaultJsonProtocol._

  val ec = ExecutionContext.Implicits.global

  implicit val sprayCounterFormat = jsonFormat3(Counter.apply)
}
