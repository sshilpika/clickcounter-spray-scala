package edu.luc.etl.cs313.scala.clickcounter.service
package api

import akka.actor._
import spray.http.MediaTypes._
import spray.http._
import spray.httpx.SprayJsonSupport
import spray.json._
import spray.routing._
import spray.routing.directives.OnCompleteFutureMagnet
import scala.util.{Success, Try}
import model.Counter
import common._
import repository.RedisRepositoryProvider

/** Actor-based wrapper for our API service. */
class ClickcounterServiceActor extends Actor with ClickcounterService with RedisRepositoryProvider {

  // the HttpService trait defines only one abstract member, which
  // connects the services environment to the enclosing actor or test
  def actorRefFactory = context

  // this actor only runs our route, but you could add
  // other things here, like request stream processing
  // or timeout handling
  def receive = runRoute(myRoute)

  def actorSystem = context.system
}

/**
 * Defines our service behavior independently from the service actor.
 * Defines the `sprayCounterFormat` possibly required by the repository provider.
 */
trait ClickcounterService extends HttpService with SprayJsonSupport with DefaultJsonProtocol {

  /** Execution context required by spray-routing. */
  implicit def executionContext = actorRefFactory.dispatcher

  /** Injected dependency on Counter repository. */
  def repository: Repository

  /** Serialization from Counter to JSON string for spray HTTP responses. */
  implicit val sprayCounterFormat = jsonFormat3(Counter.apply)

  def repoErrorHandler[T]: PartialFunction[Try[T], Route] = {
    case Success(_) => complete(StatusCodes.NotFound)
    case _ => complete(StatusCodes.InternalServerError)
  }

  def onCompleteWithRepoErrorHandler[T](m: OnCompleteFutureMagnet[T])(body: PartialFunction[Try[T], Route]) =
    onComplete(m)(body orElse repoErrorHandler)

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
                  complete(StatusCodes.Created, HttpHeaders.Location(loc) :: Nil, "")
              }
            parameters('min.as[Int], 'max.as[Int]) { (min, max) =>
              createIt(Counter(min, min, max))
            } ~
            entity(as[Counter]) {
              createIt
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
        def updateIt(f: Counter => Int, errorMsg: String) =
          onCompleteWithRepoErrorHandler(repository.update(id, f)) {
            case Success(Some(true)) => complete(StatusCodes.NoContent)
            case Success(Some(false)) => complete(StatusCodes.Conflict, errorMsg)
          }
        path("increment") {
          post {
            updateIt(_.value + 1, "counter at max, cannot increment")
          }
        } ~
        path("decrement") {
          post {
            updateIt(_.value - 1, "counter at min, cannot decrement")
          }
        } ~
        path("reset") {
          post {
            updateIt(_.min, "counter at min, cannot decrement")
          }
        } ~
        path("stream") {
          get {
            sendStreamingResponse(id)
          }
        }
      }
    }

  def sendStreamingResponse(id: String)(ctx: RequestContext): Unit = {
    repository.get(id) onComplete (({
      case Success(Some(c @ Counter(min, value, max))) =>

        val responseStart = HttpResponse(entity = HttpEntity(`application/json`, c.toJson.toString))
        ctx.responder ! ChunkedResponseStart(responseStart)

        repository.subscribe(id) {
          case Some(counter) =>
            ctx.responder ! MessageChunk(counter.toJson.toString)
          case None =>
            () // FIXME log error
        }

    }: PartialFunction[Try[Option[Counter]], Unit]) orElse repoErrorHandler)
  }
}
