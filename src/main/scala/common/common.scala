package edu.luc.etl.cs313.scala.clickcounter.service
package common

import scala.concurrent.Future
import model.Counter

/** A repository for counter domain objects. */
trait Repository {
  def keys: Future[Set[String]]
  def set(id: String, counter: Counter): Future[Boolean]
  def del(id: String): Future[Long]
  def get(id: String): Future[Option[Counter]]
  def update(id: String, f: Counter => Int): Future[Option[Boolean]]
  def subscribe(id: String)(handler: Option[Counter] => Unit): Future[Int]
}
