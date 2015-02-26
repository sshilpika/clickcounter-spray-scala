package edu.luc.etl.cs313.scala.clickcounter.service
package repository

import java.net.URI
import scredis._
import scredis.serialization.{Reader, Writer}
import spray.http._
import spray.httpx.SprayJsonSupport
import spray.json._
import scala.concurrent.Future
import scala.util.{Failure, Properties, Success, Try}
import model.Counter
import common._

trait RedisRepository extends Repository with NeedsExecutionContext with SprayJsonSupport {

  lazy val repository = this

  /** Serialization between Counter and JSON string provided by spray service. */
  implicit def sprayCounterFormat: RootJsonFormat[Counter]

  /** Serialization from Counter to JSON string for Redis set. */
  implicit val redisCounterWriter = new Writer[Counter] {
    override def writeImpl(value: Counter): Array[Byte] = sprayCounterFormat.write(value).toString.getBytes
  }

  /** Parsing of JSON string as Counter for Redis get. */
  implicit val redisCounterReader = new Reader[Counter] {
    override def readImpl(bytes: Array[Byte]): Counter = new String(bytes).parseJson.convertTo[Counter]
  }

  val url = new URI(Properties.envOrElse("REDISCLOUD_URL", "redis://localhost:6379"))

  val redis = Redis(url.getHost, url.getPort)
  if (url.getUserInfo != null) {
    val secret = url.getUserInfo.split(':')(1)
    redis.auth(secret)
  }

  val REDIS_KEY_SCHEMA = "edu.luc.etl.cs313.scala.clickcounter:"

  override def keys = redis.keys(REDIS_KEY_SCHEMA + "*")

  override def set(id: String, counter: Counter) = redis.set(REDIS_KEY_SCHEMA + id, counter)

  override def del(id: String) = redis.del(REDIS_KEY_SCHEMA + id)

  override def get(id: String) = redis.get[Counter](REDIS_KEY_SCHEMA + id)

  // TODO express results without HTTP status codes
  override def update(id: String, f: Int => Int) = {
    val key = REDIS_KEY_SCHEMA + id
    redis.watch(key)
    redis.get[Counter](key) flatMap {
      case Some(c@Counter(min, value, max)) =>
        Try { Counter(min, f(value), max) } match {
          case Success(newCounter) =>
            redis.withTransaction { t =>
              t.set(key, newCounter)
            } flatMap {
              case true => Future { newCounter }
              case false => Future { StatusCodes.InternalServerError }
            }
          case Failure(_) => Future { StatusCodes.Conflict }
        }
      case None => Future { StatusCodes.NotFound }
    }
  }
}
