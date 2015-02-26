package edu.luc.etl.cs313.scala.clickcounter.service
package model

import org.specs2.matcher.JsonMatchers
import org.specs2.mutable._

class CounterSpec extends Specification with JsonMatchers {

  "The Counter model" should {

    "allow the creation of a valid counter" in {
      val Counter(min, value, max) = Counter(0, 0, 5)
      (min, value, max) must beEqualTo (0, 0, 5)
    }

    "require min < max" in {
      Counter(0, 0, 0) must throwA[Throwable]
    }

    "require min <= value <= max" in {
      Counter(0, 6, 5) must throwA[Throwable]
    }
  }

  "The spray JSON marshaling" should {

    import spray.json._
    import DefaultJsonProtocol._

    implicit val sprayCounterFormat = jsonFormat3(Counter.apply)

    def beEqualToDouble(d: Double) = beEqualTo(d) ^^ ((_: String).toDouble)

    "write a counter to JSON" in {
      val c = Counter(1, 2, 3)
      val j = c.toJson.toString
      j must / ("min" -> beEqualToDouble(1))
      j must / ("value" -> beEqualToDouble(2))
      j must / ("max" -> beEqualToDouble(3))
    }

    "read a counter from JSON" in {
      val j = """{ "min": 1, "value": 2, "max": 3 }"""
      val c = Counter(1, 2, 3)
      j.parseJson.convertTo[Counter] must beEqualTo(c)
    }
  }
}
