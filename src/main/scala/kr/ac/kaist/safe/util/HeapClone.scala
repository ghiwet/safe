/**
 * *****************************************************************************
 * Copyright (c) 2016-2017, KAIST.
 * All rights reserved.
 *
 * Use is subject to license terms.
 *
 * This distribution may include materials developed by third parties.
 * ****************************************************************************
 */

package kr.ac.kaist.safe.util

import kr.ac.kaist.safe.analyzer.{ CallSiteContext, ProductTP, TracePartition }
import kr.ac.kaist.safe.analyzer.domain.Loc

import scala.collection.mutable.ArrayBuffer
import scala.util.Try
import spray.json._

case class HeapClone(
    loc: Loc,
    tp: TracePartition
) extends Loc {
  override def toString: String = s"${loc}_${HeapClone.getIndex(this)}"
  def toJson: JsValue = JsObject(
    ("loc", loc.toJson)
  // TODO: heap cloning
  )
}

object HeapClone {

  private val heapClones = new ArrayBuffer[HeapClone]()

  def getIndex(hc: HeapClone): Int = {
    // TODO: Tree datastructure for performance?
    heapClones.indexOf(hc)
  }

  def getHeapClone(index: Int): Try[HeapClone] = {
    Try(heapClones(index))
  }

  private def addHC(loc: Loc, tp: TracePartition) = loc match {
    case Recency(subLoc, Recent) =>
      val hc = HeapClone(subLoc, tp)
      heapClones += hc
      Recency(hc, Recent)
    case _ =>
      val hc = HeapClone(loc, tp)
      heapClones += hc
      hc
  }

  def add(loc: Loc, tp: TracePartition): Loc = tp match {
    case CallSiteContext(csList, _) => csList match {
      case Nil => loc
      case _ => addHC(loc, tp)
    }
    case ProductTP(ltp, _) => add(loc, ltp)
    case _ => loc
  }

}
