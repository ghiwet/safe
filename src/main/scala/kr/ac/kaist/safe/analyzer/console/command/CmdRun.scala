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

package kr.ac.kaist.safe.analyzer.console.command

import kr.ac.kaist.safe.analyzer.console._
import kr.ac.kaist.safe.analyzer.console.command.CmdHelp.printResult

// run
case object CmdRun extends Command("run", "Run until meet some break point.") {
  def run(c: Interactive, args: List[String]): Option[Target] = {
    val subPattern = "(-v)".r
    args match {
      case subcmd :: Nil => subcmd match {
        case "-v" => Some(TargetIter(-1, true))
        case _ => printResult("* '" + subcmd + "' is not a valid sub command."); None
      }
      case Nil => Some(TargetIter(-1))
      case _ => printResult(help); None
    }
  }
}
