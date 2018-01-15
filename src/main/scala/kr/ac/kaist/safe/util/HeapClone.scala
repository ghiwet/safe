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

import kr.ac.kaist.safe.analyzer.CallSiteContext
import kr.ac.kaist.safe.analyzer.domain.Loc
import kr.ac.kaist.safe.nodes.cfg.Call

import scala.collection.mutable.ArrayBuffer
import scala.util.Try
import spray.json._

case class HeapClone(
    loc: Loc,
    csList: List[Call]
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

  def apply(loc: Loc, cc: CallSiteContext): Loc = cc.callsiteList match {
    case csList @ (_ :: _) => loc match {
      case Recency(subLoc, Recent) =>
        val hc = HeapClone(subLoc, csList)
        heapClones += hc
        Recency(hc, Recent)
      case _ =>
        val hc = HeapClone(loc, csList)
        heapClones += hc
        hc
    }
    case Nil => loc
  }

}
