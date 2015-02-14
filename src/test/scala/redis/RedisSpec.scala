package edu.luc.etl.cs313.scala.clickcounter.service.redis

import java.net.URI
import scala.util.Properties
import com.redis._
import org.specs2.mutable.Specification

class RedisSpec extends Specification {

  "The Redis store" should {
    "return the stored value" in {
      val url = new URI(Properties.envOrElse("REDISCLOUD_URL", "redis://localhost:6379"))
      println("url = " + url)
      println("auth = " + url.getAuthority)
      println("userInfo = " + url.getUserInfo)
      val client = new RedisClient(url.getHost, url.getPort)
      if (url.getUserInfo != null) {
        client.auth(url.getUserInfo)
      }
      val key = "hello"
      val value = "world"
      client.set(key, value)
      client.get(key) must beSome(value)
    }
  }
}
