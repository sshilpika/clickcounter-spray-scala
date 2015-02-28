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
 * Stackable mixin trait that provides a Redis repository.
 * It requires a `sprayCounterFormat` for serialization.
 */
trait RedisRepositoryProvider extends NeedsActorSystem {

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
  for (userInfo <- Option(url.getUserInfo)) {
    val secret = userInfo.split(':')(1)
    redis.auth(secret)
  }

  val REDIS_KEY_SCHEMA = "edu.luc.etl.cs313.scala.clickcounter:"

  def toKey(id: String) = REDIS_KEY_SCHEMA + id

  object repository extends Repository {

    import redis.dispatcher

    override def keys =
      for { result <- redis.keys(toKey("*")) } yield
        for { s <- result } yield s.split(':')(1)

    override def set(id: String, counter: Counter) = {
      val key = toKey(id)
      val value = counter.toJson.toString
      redis.set(key, value) map { result =>
        redis.publish(key, counter)
        result
      }
    }

    override def del(id: String) = redis.del(toKey(id))

    override def get(id: String) = redis.get[Counter](toKey(id))

    /**
     * @return A future with the following content:
     *         if item not found, `None`;
     *         if update succeeded, `Some(true)`;
     *         otherwise `Some(false)`.
     */
    override def update(id: String, f: Counter => Int) = {
      val key = toKey(id)
      redis.watch(key) flatMap { _ =>
        // lock key optimistically
        redis.get[Counter](key) flatMap {
          case Some(c@Counter(min, value, max)) =>
            // found item, attempt update
            Try(Counter(min, f(c), max)) match {
              case Success(newCounter) =>
                // map Future[Boolean] to Future[Option[Boolean]]
                redis.withTransaction { t => t.set(key, newCounter) } map { result =>
                  redis.publish(key, newCounter)
                  Some(result)
                }
              case Failure(_) =>
                // precondition for update not met
                Future.successful(Some(false))
            }
          case None => Future.successful(None) // item not found
        }
      }
    }

    override def subscribe(id: String)(handler: Option[Counter] => Unit) = {
      val key = toKey(id)
      val subscriber = SubscriberClient(url.getHost, url.getPort) // requires actorSystem!
      for (userInfo <- Option(url.getUserInfo)) {
        val secret = userInfo.split(':')(1)
        subscriber.auth(secret)
      }
      subscriber.subscribe(key) {
        case PubSubMessage.Message(channel, messageBytes) =>
          handler(Try(new String(messageBytes).parseJson.convertTo[Counter]).toOption)
        case PubSubMessage.Error(e) =>
          handler(None)
      }
    }
  }
}
