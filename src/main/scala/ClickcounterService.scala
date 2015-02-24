package edu.luc.etl.cs313.scala.clickcounter.service

import scala.util.{Properties, Try, Success}
import java.net.URI
import com.redis.RedisClient
import akka.actor.Actor
import spray.routing._
import spray.http._
import MediaTypes._
import spray.httpx.SprayJsonSupport
import spray.httpx.marshalling.ToResponseMarshaller
import spray.json._


// we don't implement our route structure directly in the service actor because
// we want to be able to test it independently, without having to spin up an actor
class ClickcounterServiceActor extends Actor with ClickcounterService {

  // the HttpService trait defines only one abstract member, which
  // connects the services environment to the enclosing actor or test
  def actorRefFactory = context

  // this actor only runs our route, but you could add
  // other things here, like request stream processing
  // or timeout handling
  def receive = runRoute(myRoute)
}

sealed trait State
case object Empty extends State
case object Counting extends State
case object Full extends State

case class Counter(min: Int, value: Int, max: Int) {
  require { min < max }
  require { min <= value && value <= max }
  def state(): State =
    if (value == min) Empty
    else if (value < max) Counting
    else Full
}

// this trait defines our service behavior independently from the service actor
trait ClickcounterService extends HttpService with SprayJsonSupport with DefaultJsonProtocol {

  /** Serialization from Counter to JSON string for spray HTTP responses. */
  implicit val sprayCounterFormat = jsonFormat3(Counter.apply)

  val pf: PartialFunction[Any, Any] = { case c: Counter => sprayCounterFormat.write(c).toString }
  /** Serialization from Counter to JSON string for Redis set. */
  implicit val redisCounterFormat = new com.redis.serialization.Format(pf)
  /** Parsing of JSON string as Counter for Redis get. */
  implicit val redisCounterParse = com.redis.serialization.Parse[Counter](new String(_).parseJson.convertTo[Counter])

  val url = new URI(Properties.envOrElse("REDISCLOUD_URL", "redis://localhost:6379"))
  val redis = new RedisClient(url.getHost, url.getPort)
  if (url.getUserInfo != null) {
    val secret = url.getUserInfo.split(':')(1)
    redis.auth(secret)
  }

  // TODO isolate all Redis stuff in a DAO

  object dao {
    val REDIS_KEY_SCHEMA = "edu.luc.etl.cs313.scala.clickcounter:"
    def set(id: String, counter: Counter): Boolean = redis.set(REDIS_KEY_SCHEMA + id, counter)
    def del(id: String): Option[Long] = redis.del(REDIS_KEY_SCHEMA + id)
    def get(id: String): Option[Counter] = redis.get[Counter](REDIS_KEY_SCHEMA + id)
  }

  val myRoute =
    pathEndOrSingleSlash {
      get {
        respondWithMediaType(`text/html`) { // XML is marshalled to `text/xml` by default, so we simply override here
          complete {
            <html>
              <body>
                <h1>Welcome to the click counter service.</h1>
              </body>
            </html>
          }
        }
      }
    } ~
    pathPrefix("counters" / Segment) { id =>
      pathEnd {
        put {
          requestUri { uri =>
            def createIt(counter: Counter) = {
              if (dao.set(id, counter)) {
                val loc = uri.copy(query = Uri.Query.Empty)
                complete(StatusCodes.Created, HttpHeaders.Location(loc) :: Nil, "")
              } else {
                complete(StatusCodes.InternalServerError)
              }
            }
            parameters('min, 'max) { (min, max) =>
              createIt(Counter(min.toInt, min.toInt, max.toInt))
            } ~
            entity(as[Counter]) { c =>
              createIt(c)
            }
          }
        } ~
        delete {
          complete {
            dao.del(id) match {
              case Some(_) => StatusCodes.NoContent
              case _ => StatusCodes.NotFound
            }
          }
        } ~
        get {
          complete {
            dao.get(id) match {
              case Some(c @ Counter(min, value, max)) => c
              case _ => StatusCodes.NotFound
            }
          }
        }
      } ~ {
        def updateIt(f: Int => Int) =
          complete {
            dao.get(id) match {
              case Some(c @ Counter(min, value, max)) =>
                Try { Counter(min, f(value), max) } match {
                  case Success(newCounter) =>
                    dao.set(id, newCounter)
                    newCounter
                  case _ => StatusCodes.PreconditionFailed
                }
              case _ => StatusCodes.NotFound
            }
          }
        path("increment") {
          post {
            updateIt(_ + 1)
          }
        } ~
        path("decrement") {
          post {
            updateIt(_ - 1)
          }
        } ~
        path("stream") {
          get {
            complete(StatusCodes.NotImplemented)
          }
        }
      }
    }
}
