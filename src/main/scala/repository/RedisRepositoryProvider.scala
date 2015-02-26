package edu.luc.etl.cs313.scala.clickcounter.service
package repository

import java.net.URI
import scredis._
import scredis.serialization.{Reader, Writer}
import spray.json._
import scala.concurrent.Future
import scala.util.{Failure, Properties, Success, Try}
import model.Counter
import common._

/**
 * Stackable mixin trait that provides a Redis repository and
 * requires a `sprayCounterFormat` and an execution context.
 */
trait RedisRepositoryProvider extends NeedsExecutionContext {

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

  object repository extends Repository {

    override def keys = redis.keys(REDIS_KEY_SCHEMA + "*")

    override def set(id: String, counter: Counter) = redis.set(REDIS_KEY_SCHEMA + id, counter)

    override def del(id: String) = redis.del(REDIS_KEY_SCHEMA + id)

    override def get(id: String) = redis.get[Counter](REDIS_KEY_SCHEMA + id)

    /**
     * @return A future with the following content:
     *         if item not found, `None`;
     *         if update succeeded, `Some(true)`;
     *         otherwise `Some(false)`.
     */
    override def update(id: String, f: Int => Int) = {
      val key = REDIS_KEY_SCHEMA + id
      redis.watch(key) // lock key optimistically
      redis.get[Counter](key) flatMap {
        case Some(c@Counter(min, value, max)) =>
          // found item, attempt update
          Try {
            Counter(min, f(value), max)
          } match {
            case Success(newCounter) =>
              // map Future[Boolean] to Future[Option[Boolean]]
              redis.withTransaction { t => t.set(key, newCounter)} map {
                Some(_)
              }
            case Failure(_) =>
              // precondition for update not met
              Future.successful(Some(false))
          }
        case None => Future.successful(None) // item not found
      }
    }
  }
}
