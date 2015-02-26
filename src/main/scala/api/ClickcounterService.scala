package edu.luc.etl.cs313.scala.clickcounter.service
package api

import akka.actor.Actor
import edu.luc.etl.cs313.scala.clickcounter.service.common.NeedsExecutionContext
import spray.http.MediaTypes._
import spray.http._
import spray.httpx.SprayJsonSupport
import spray.json._
import spray.routing._
import spray.routing.directives.OnCompleteFutureMagnet
import scala.util.{Success, Try}
import model.Counter
import common.Repository
import repository.RedisRepository

// we don't implement our route structure directly in the service actor because
// we want to be able to test it independently, without having to spin up an actor
class ClickcounterServiceActor extends Actor with ClickcounterService with RedisRepository {

  // the HttpService trait defines only one abstract member, which
  // connects the services environment to the enclosing actor or test
  def actorRefFactory = context

  // this actor only runs our route, but you could add
  // other things here, like request stream processing
  // or timeout handling
  def receive = runRoute(myRoute)

  /** Execution context required by the nonblocking Redis client. */
  lazy val ec = context.dispatcher
}

// this trait defines our service behavior independently from the service actor
trait ClickcounterService extends HttpService with SprayJsonSupport with DefaultJsonProtocol with NeedsExecutionContext {

  /** Injected dependency on Counter repository. */
  def repository: Repository

  /** Serialization from Counter to JSON string for spray HTTP responses. */
  implicit val sprayCounterFormat = jsonFormat3(Counter.apply)

  def onCompleteWithRepoErrorHandler[T](m: OnCompleteFutureMagnet[T])(body: PartialFunction[Try[T], Route]) =
    onComplete(m)(body orElse {
      case Success(_) => complete(StatusCodes.NotFound)
      case _ => complete(StatusCodes.InternalServerError)
    })

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
    path("counters") {
      get {
        onSuccess(repository.keys) { complete(_) }
      }
    } ~
    pathPrefix("counters" / Segment) { id =>
      pathEnd {
        put {
          requestUri { uri =>
            def createIt(counter: Counter) =
              onCompleteWithRepoErrorHandler(repository.set(id, counter)) {
                case Success(true) =>
                  val loc = uri.copy(query = Uri.Query.Empty)
                  complete(StatusCodes.Created, HttpHeaders.Location(loc) :: Nil, counter)
              }
            parameters('min.as[Int], 'max.as[Int]) { (min, max) =>
              createIt(Counter(min, min, max))
            } ~
            entity(as[Counter]) { c =>
              createIt(c)
            }
          }
        } ~
        delete {
          onCompleteWithRepoErrorHandler(repository.del(id)) {
            case Success(1) => complete(StatusCodes.NoContent)
          }
        } ~
        get {
          onCompleteWithRepoErrorHandler(repository.get(id)) {
            case Success(Some(c @ Counter(min, value, max))) => complete(c)
          }
        }
      } ~ {
        def updateIt(f: Int => Int, errorMsg: String) =
          onCompleteWithRepoErrorHandler(repository.update(id, f)) {
            case Success(r) => complete(r)

          }
        path("increment") {
          post {
            updateIt(_ + 1, "counter at max, cannot increment")
          }
        } ~
        path("decrement") {
          post {
            updateIt(_ - 1, "counter at min, cannot decrement")
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
