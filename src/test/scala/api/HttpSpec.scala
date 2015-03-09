package edu.luc.etl.cs313.scala.clickcounter.service
package api

import dispatch._
import org.specs2.matcher.JsonMatchers
import org.specs2.mutable._

/** Concrete test of Apiary mock server. */
class ApiaryHttpSpec extends HttpSpec {
  val serviceRoot = host("private-14b8e-clickcounter.apiary-mock.com")
}

/** Concrete test of locally running service instance. */
class LocalHttpSpec extends HttpSpecWithIncrement {
  val serviceRoot = host("localhost", 8080)
}

/** Concrete test of service instance running on Heroku. */
class HerokuHttpSpec extends HttpSpecWithIncrement {
  val serviceRoot = host("laufer-clickcounter.herokuapp.com")
}

/** Testclass superclass for HTTP-based, in-container test of the deployed service. */
trait HttpSpec extends Specification with JsonMatchers {

  sequential

  def serviceRoot: Req

  def beEqualToDouble(d: Double) = beEqualTo(d) ^^ ((_: String).toDouble)

  val cMin = 0

  val cMax = 5

  val id = "123"

  "The click counter service, on its collection of counters," should {

    "allow the creation of a new counter" in {
      val request = serviceRoot / "counters" / id <<? Map("min" -> cMin.toString, "max" -> cMax.toString)
      val response = Http(request.PUT)
      response().getStatusCode === 201
    }

    "include the newly created counter in the list of counters" in {
      val request = serviceRoot / "counters"
      val response = Http(request OK as.String)
      response() contains id
    }

    "retrieve an existing counter" in {
      val request = serviceRoot / "counters" / id
      val response = Http(request OK as.String)
      val counter = response()
      counter must / ("min" -> beEqualToDouble(cMin))
      counter must / ("value" -> beEqualToDouble(cMin))
      counter must / ("max" -> beEqualToDouble(cMax))
    }

    "delete an existing counter" in {
      val request = serviceRoot / "counters" / id
      val response = Http(request.DELETE)
      response().getStatusCode === 204
    }
  }

  "The click counter service, on a specific counter," should {

    "allow the creation of a new counter" in {
      val request = serviceRoot / "counters" / id <<? Map("min" -> cMin.toString, "max" -> cMax.toString)
      val response = Http(request.PUT)
      response().getStatusCode === 201
    }

    "refuse to decrement the counter initially" in {
      val request = serviceRoot / "counters" / id / "decrement"
      val response = Http(request.POST)
      response().getStatusCode === 409
    }

    "reset the counter" in {
      val request = serviceRoot / "counters" / id / "reset"
      val response = Http(request.POST)
      val result = response()
      result.getStatusCode === 204
      val request2 = serviceRoot / "counters" / id
      val response2 = Http(request2 OK as.String)
      val counter = response2()
      counter must / ("min" -> beEqualToDouble(cMin))
      counter must / ("value" -> beEqualToDouble(cMin))
      counter must / ("max" -> beEqualToDouble(cMax))
    }

    "retrieve a counter value stream" in {
      todo
    }
  }
}

trait HttpSpecWithIncrement extends HttpSpec {

  "The click counter service, on a specific counter," should {

    "increment the counter" in {
      val request = serviceRoot / "counters" / id / "increment"
      val response = Http(request.POST)
      val result = response()
      result.getStatusCode === 204
      val request2 = serviceRoot / "counters" / id
      val response2 = Http(request2 OK as.String)
      val counter = response2()
      counter must /("min" -> beEqualToDouble(cMin))
      counter must /("value" -> beEqualToDouble(cMin + 1))
      counter must /("max" -> beEqualToDouble(cMax))
    }
  }
}
