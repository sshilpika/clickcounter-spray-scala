package edu.luc.etl.cs313.scala.clickcounter.service
package repository

import java.util.UUID

import org.specs2.mutable.Specification
import common.Repository
import model.Counter

trait RepositorySpec extends Specification {

  def fixture: Repository

  val key = UUID.randomUUID.toString

  "The repository" should {

    "not retrieve a nonexisting item" in {
      val repo = fixture
      repo.get(key) must beNone.await
    }

    "retrieve an existing item" in {
      val repo = fixture
      val c = Counter(1, 2, 3)
      repo.set(key, c)
      repo.get(key) must beSome(c).await
    }

    "delete an existing item" in {
      val repo = fixture
      val c = Counter(1, 2, 3)
      repo.set(key, c)
      repo.del(key) must beEqualTo(1).await
    }

    "not update an nonexisting item" in {
      val repo = fixture
      val c = Counter(1, 2, 3)
      repo.update(key, _ + 1) must beNone.await
    }

    "update an existing item" in {
      val repo = fixture
      val c = Counter(1, 2, 3)
      repo.set(key, c)
      repo.update(key, _ + 1) must beSome(true).await
    }

    "not update an existing item when preconditions are violated" in {
      val repo = fixture
      val c = Counter(1, 3, 3)
      repo.set(key, c)
      repo.update(key, _ + 1) must beSome(false).await
    }
  }
}
