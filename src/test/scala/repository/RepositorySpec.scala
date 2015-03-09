package edu.luc.etl.cs313.scala.clickcounter.service
package repository

import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.duration._
import org.specs2.mutable.{After, Specification}
import common.Repository
import model.Counter

trait RepositorySpec extends Specification {

  /** Injected dependency on Counter repository. */
  def repository: Repository

  "The repository" should {

    "not retrieve a nonexisting item" in new FixtureContext {
      repo.get(key) must beNone.await
    }

    "retrieve an existing item" in new FixtureContext {
      val c = Counter(1, 2, 3)
      repo.set(key, c) flatMap { _ => repo.get(key) } must beSome(c).await
    }

    "delete an existing item" in new FixtureContext {
      val c = Counter(1, 2, 3)
      repo.set(key, c) flatMap { _ => repo.del(key) } must beEqualTo(1).await
    }

    "not update an nonexisting item" in new FixtureContext {
      val c = Counter(1, 2, 3)
      repo.update(key, _.value + 1) must beNone.await
    }

    "update an existing item" in new FixtureContext {
      val c = Counter(1, 2, 3)
      repo.set(key, c) flatMap { _ => repo.update(key, _.value + 1) } must beSome(true).await
    }

    "not update an existing item when preconditions are violated" in new FixtureContext {
      val c = Counter(1, 3, 3)
      repo.set(key, c) flatMap { _ => repo.update(key, _.value + 1) } must beSome(false).await
    }
  }

  trait FixtureContext extends After {
    val repo = repository
    val key = UUID.randomUUID.toString
    def after = {
      Thread.sleep(50) // give the current transaction some time to finish
      Await.result(repo.del(key), FiniteDuration(1, SECONDS))
    }
  }
}
