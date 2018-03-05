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

package kr.ac.kaist.safe.analyzer.domain

import kr.ac.kaist.safe.analyzer._
import kr.ac.kaist.safe.errors.error._
import kr.ac.kaist.safe.util._

import scala.collection.immutable.HashSet
import scala.util.{ Failure, Success, Try }
import spray.json._

// concrete location type
abstract class Loc extends Value {
  def isUser: Boolean = this match {
    case Recency(loc, _) => loc.isUser
    case UserAllocSite(_) => true
    case PredAllocSite(_) => false
    case HeapClone(loc, _) => loc.isUser
  }

  override def toString: String = this match {
    case Recency(loc, _) => loc.toString
    case u @ UserAllocSite(_) => throw UserAllocSiteError(u)
    case p @ PredAllocSite(_) => p.toString
  }

  def toJson: JsValue
}

object Loc {
  // predefined special concrete location
  lazy val predConSet: Set[Loc] = HashSet(
    PredAllocSite.GLOBAL_ENV,
    PredAllocSite.PURE_LOCAL
  )
  // heap cloning flag
  var heapCloning = false

  def parse(str: String): Try[Loc] = {
    val heapClone = ".+_([0-9]+)".r
    val recency = "(R|O)(.+)".r
    val userASite = "#([0-9]+)".r
    val predASite = "#([0-9a-zA-Z-.<>]+)".r
    str match {
      // allocation site
      case userASite(id) => Try(UserAllocSite(id.toInt))
      case predASite(name) => Success(PredAllocSite(name))
      // recency abstraction
      case recency("R", str) => parse(str).map(Recency(_, Recent))
      case recency("O", str) => parse(str).map(Recency(_, Old))
      // heap cloning
      case heapClone(cid) => Try(cid.toInt).flatMap(HeapClone.getHeapClone(_))
      // otherwise
      case str => Failure(NoLoc(str))
    }
  }

  def apply(str: String): Loc = apply(PredAllocSite(str))
  def apply(asite: AllocSite): Loc = AAddrType match {
    case NormalAAddr => asite
    case RecencyAAddr => Recency(asite, Recent)
  }
  def apply(asite: AllocSite, tp: TracePartition): Loc = if (!heapCloning) apply(asite) else HeapClone.add(apply(asite), tp)

  implicit def ordering[B <: Loc]: Ordering[B] = Ordering.by({
    case addrPart => addrPart.toString
  })

  def fromJson(v: JsValue): Loc = v match {
    case JsObject(m) => (
      m.get("loc").map(Loc.fromJson _),
      m.get("recency").map(RecencyTag.fromJson _)
    // TODO: heap cloning
    ) match {
        case (Some(l), Some(r)) => Recency(l, r)
        case _ => throw RecencyParseError(v)
      }
    case _ => AllocSite.fromJson(v)
  }
}
