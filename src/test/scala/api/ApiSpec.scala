package edu.luc.etl.cs313.scala.clickcounter.service
package api

import org.specs2.matcher.JsonMatchers
import org.specs2.mutable._
import spray.http.StatusCodes._
import spray.testkit.Specs2RouteTest
import repository.InMemoryRepositoryProvider

/** Unit test of API routes using an in-memory repository as a mock. */
class ConcreteApiSpec extends ApiSpec with InMemoryRepositoryProvider

/** Testcase superclass for out-of-container testing of API routes. */
trait ApiSpec extends Specification with Specs2RouteTest with JsonMatchers with ClickcounterService {

  sequential

  def actorRefFactory = system // connect the DSL to the test ActorSystem

  lazy val ec = system.dispatcher

  def beEqualToDouble(d: Double) = beEqualTo(d) ^^ ((_: String).toDouble)

  val cMin = 0

  val cMax = 5

  val id = "123"

  "The click counter service, on its collection of counters," should {

    "allow the creation of a new counter" in {
      Put("/counters/" + id + "?min=0&max=5") ~> myRoute ~> check {
        status === Created
      }
    }

    "include the newly created counter in the list of counters" in {
      Get("/counters") ~> myRoute ~> check {
        status === OK
        responseAs[String] must contain("123")
      }
    }

    "retrieve an existing counter" in {
      Get("/counters/" + id) ~> myRoute ~> check {
        status === OK
        val counter = responseAs[String]
        counter must / ("min" -> beEqualToDouble(cMin))
        counter must / ("value" -> beEqualToDouble(cMin))
        counter must / ("max" -> beEqualToDouble(cMax))
      }
    }

    "delete an existing counter" in {
      Delete("/counters/" + id) ~> myRoute ~> check {
        status === NoContent
      }
    }
  }

  "The click counter service, on a specific counter," should {

    "allow the creation of a new counter" in {
      Put("/counters/" + id + "?min=0&max=5") ~> myRoute ~> check {
        status === Created
      }
    }

    "refuse to decrement the counter initially" in {
      Post("/counters/" + id + "/decrement") ~> myRoute ~> check {
        status === Conflict
      }
    }

    "increment the counter" in {
      Post("/counters/" + id + "/increment") ~> myRoute ~> check {
        status === NoContent
      }
      Get("/counters/" + id) ~> myRoute ~> check {
        status === OK
        val counter = responseAs[String]
        counter must / ("min" -> beEqualToDouble(cMin))
        counter must / ("value" -> beEqualToDouble(cMin + 1))
        counter must / ("max" -> beEqualToDouble(cMax))
      }
    }

    "reset the counter" in {
      Post("/counters/" + id + "/reset") ~> myRoute ~> check {
        status === NoContent
      }
      Get("/counters/" + id) ~> myRoute ~> check {
        status === OK
        val counter = responseAs[String]
        counter must / ("min" -> beEqualToDouble(cMin))
        counter must / ("value" -> beEqualToDouble(cMin))
        counter must / ("max" -> beEqualToDouble(cMax))
      }
    }

    "retrieve a counter value stream" in {
      todo
    }
  }
}
