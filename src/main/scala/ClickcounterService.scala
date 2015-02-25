package edu.luc.etl.cs313.scala.clickcounter.service

import spray.routing.directives.OnCompleteFutureMagnet

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Properties, Try, Failure, Success}
import java.net.URI
import scredis._
import scredis.serialization.{Reader, Writer}
import akka.actor.Actor
import spray.routing._
import spray.http._
import MediaTypes._
import spray.httpx.SprayJsonSupport
import spray.httpx.marshalling.ToResponseMarshallable
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

  import context.dispatcher

  val ec = dispatcher
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

  implicit val ec: ExecutionContext

  /** Serialization from Counter to JSON string for spray HTTP responses. */
  implicit val sprayCounterFormat = jsonFormat3(Counter.apply)

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

  object dao {
    val REDIS_KEY_SCHEMA = "edu.luc.etl.cs313.scala.clickcounter:"
    def set(id: String, counter: Counter): Future[Boolean] = redis.set(REDIS_KEY_SCHEMA + id, counter)
    def del(id: String): Future[Long] = redis.del(REDIS_KEY_SCHEMA + id)
    def get(id: String): Future[Option[Counter]] = redis.get[Counter](REDIS_KEY_SCHEMA + id)
    def update(id: String, f: Int => Int): Future[ToResponseMarshallable] = {
      val key = REDIS_KEY_SCHEMA + id
      redis.watch(key)
      val future1 = redis.get[Counter](key)
      future1 flatMap {
        case Some(c @ Counter(min, value, max)) =>
          Try { Counter(min, f(value), max) } match {
            case Success(newCounter) =>
              redis.withTransaction { t =>
                t.set(key, newCounter)
              } flatMap {
                case true => Future { newCounter }
                case false => Future { StatusCodes.InternalServerError }
              }
            case Failure(_) => Future { StatusCodes.PreconditionFailed }
          }
        case None => Future { StatusCodes.NotFound }
      }
    }
  }

  val daoErrorHandler: PartialFunction[Any, Route] = {
    case Success(_) => complete(StatusCodes.NotFound)
    case _ => complete(StatusCodes.InternalServerError)
  }

  def onCompleteWithDaoErrorHandler[T](m: OnCompleteFutureMagnet[T])(body: PartialFunction[Try[T], Route]) =
    onComplete(m)(body orElse daoErrorHandler)

  val myRoute =
    pathEndOrSingleSlash {
      get {
        respondWithMediaType(`text/html`) {
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
            def createIt(counter: Counter) =
              onCompleteWithDaoErrorHandler(dao.set(id, counter)) {
                case Success(true) =>
                  val loc = uri.copy(query = Uri.Query.Empty)
                  complete(StatusCodes.Created, HttpHeaders.Location(loc) :: Nil, counter)
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
          onCompleteWithDaoErrorHandler(dao.del(id)) {
            case Success(1) => complete(StatusCodes.NoContent)
          }
        } ~
        get {
          onCompleteWithDaoErrorHandler(dao.get(id)) {
            case Success(Some(c @ Counter(min, value, max))) => complete(c)
          }
        }
      } ~ {
        def updateIt(f: Int => Int) =
          onCompleteWithDaoErrorHandler(dao.update(id, f)) {
            case Success(r) => complete(r)
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
