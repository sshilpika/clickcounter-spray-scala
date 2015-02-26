package edu.luc.etl.cs313.scala.clickcounter.service
package common

import spray.httpx.marshalling.ToResponseMarshallable
import scala.concurrent.{ExecutionContext, Future}
import model.Counter

/** A repository for counters. */
trait Repository {
  def keys: Future[Set[String]]
  def set(id: String, counter: Counter): Future[Boolean]
  def del(id: String): Future[Long]
  def get(id: String): Future[Option[Counter]]
  def update(id: String, f: Int => Int): Future[ToResponseMarshallable]
}

/** Injected dependency on an execution context required to handle futures. */
trait NeedsExecutionContext {
  implicit def ec: ExecutionContext
}
