package edu.luc.etl.cs313.scala.clickcounter.service
package model

/** Immutable domain model. */
case class Counter(min: Int, value: Int, max: Int) {
  require { min < max }
  require { min <= value && value <= max }
}
