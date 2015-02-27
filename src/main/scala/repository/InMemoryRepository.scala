package edu.luc.etl.cs313.scala.clickcounter.service
package repository

import scala.concurrent.Future
import scala.collection.mutable.Map
import scala.util.{Failure, Success, Try}
import common.Repository
import model.Counter

/** Stackable mixin trait that provides a fake in-memory repository. */
trait InMemoryRepositoryProvider {
  lazy val repository = new InMemoryRepository
}

/** Fake thread-safe in-memory repository for unit testing. */
class InMemoryRepository extends Repository {

  private val data = Map.empty[String, Counter]

  override def keys: Future[Set[String]] = synchronized { Future.successful(data.keys.toSet) }

  override def set(id: String, counter: Counter) = synchronized { data.put(id, counter) ; Future.successful(true) }

  override def get(id: String) = synchronized { Future.successful(data.get(id)) }

  override def del(id: String) = synchronized { data.remove(id) ; Future.successful(1) }

  override def update(id: String, f: Counter => Int) = synchronized {
    data.get(id) match {
      case Some(c @ Counter(min, value, max)) =>
        // found item, attempt update
        Try { Counter(min, f(c), max) } match {
          case Success(newCounter) =>
            data.put(id, newCounter)
            Future.successful(Some(true))
          case Failure(_) =>
            // precondition for update not met
            Future.successful(Some(false))
        }
      case None => Future.successful(None) // item not found
    }
  }
}
