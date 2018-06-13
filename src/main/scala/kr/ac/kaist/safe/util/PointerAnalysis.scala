
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

import kr.ac.kaist.safe.analyzer.domain._
import kr.ac.kaist.safe.analyzer.domain.DefaultHeap.HeapMap
import kr.ac.kaist.safe.analyzer.domain.DefaultNumber.{ NUInt, UInt }
import kr.ac.kaist.safe.analyzer.models.builtin.BuiltinGlobal
import kr.ac.kaist.safe.analyzer.{ ControlPoint, Semantics, TracePartition }
import kr.ac.kaist.safe.nodes.cfg.CFG

class PointerAnalysis(
    sem: Semantics,
    cfg: CFG
) {

  def avgPtsSize(initTP: TracePartition): Double = {
    var asiteObjMap: Map[AllocSite, Set[AbsObj]] = Map()
    val exitCP = ControlPoint(cfg.globalFunc.exit, initTP)
    val state = sem.getState(exitCP)
    var avgStrcount = 0.0
    var avgPropCount = 0.0
    var strUndefCount = 0.0
    var UIntCount = 0
    state.heap match {
      case heap @ HeapMap(map) => {

        map.foreach({
          case (loc, obj) => if (loc.isUser || loc == BuiltinGlobal.loc) {
            val asite = loc.getLocASite
            asiteObjMap.get(asite) match {
              case Some(objects) => asiteObjMap += asite -> (objects + obj)
              case None => asiteObjMap += asite -> Set(obj)
            }
          }
        })
      }
    }
    asiteObjMap.foreach({
      case (asite, objects) =>
        var strCount = 0.0
        var propCount = 0.0
        var underCount = 0
        objects.foreach(obj => {
          val nmap = obj.nmap
          nmap.map.foreach({
            case (str, dataprop) => {
              str match {
                case value: String if value.contains("length") =>
                case _ => dataprop.content.value.locset match {
                  case locset: AbsLoc =>
                    var isModel = false
                    var size = 0
                    locset.foreach(l => {
                      if (asite.isUser || l.isUser) {
                        size += 1
                      } else {
                        isModel = true
                      }
                    })
                    if (size > 1 || (size == 1 && isModel == false)) {
                      if (!dataprop.content.value.pvalue.toString.contains("Top(undefined)")) {
                        strCount += 1
                        propCount += size
                      }
                    } else if (!dataprop.content.value.pvalue.isBottom && !dataprop.content.value.pvalue.toString.contains("Top(undefined)")) {

                      val strPtsSize = dataprop.content.value.pvalue match {
                        case pvalue: AbsPValue if (!pvalue.strval.isBottom) => pvalue.strval match {
                          case strValue: AbsStr if (strValue.isInstanceOf[StringSet#StrSet]) =>
                            val str = strValue.asInstanceOf[StringSet#StrSet]
                            str.values.size
                          case _ => 1
                        }
                        case pvalue: AbsPValue if (!pvalue.numval.isBottom) => pvalue.numval match {
                          case numValue: AbsNum if (numValue.equals(DefaultNumber.UInt) || numValue.equals(DefaultNumber.NUInt)) =>
                            UIntCount += 1
                            1
                          case _ => 1
                        }
                        case _ => 1
                      }
                      strCount += 1
                      propCount += strPtsSize
                    } else if (dataprop.content.value.pvalue.toString.contains("Top(undefined)") && str != "undefined")
                      underCount += 1
                }

              }

            }
          })
        })
        avgStrcount += strCount / objects.size
        avgPropCount += propCount / objects.size
        strUndefCount += underCount / objects.size
      case _ =>
    })
    System.out.println("Total number of properties: " + (avgStrcount - 3))
    System.out.println("Total number of properties pointing to undefined: " + strUndefCount)
    System.out.println("Percentage of properties pointing to undefined: " + 100 * strUndefCount / (strUndefCount + avgStrcount - 3))
    System.out.println("Number properties pointing to UInt/NUInt: " + UIntCount)
    (avgPropCount - 3) / (avgStrcount - 3)
  }

};