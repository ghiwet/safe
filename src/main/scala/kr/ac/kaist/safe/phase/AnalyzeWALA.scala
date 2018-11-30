package kr.ac.kaist.safe.phase

import kr.ac.kaist.safe.SafeConfig
import kr.ac.kaist.safe.analyzer._
import kr.ac.kaist.safe.analyzer.domain.{ AAddrType, BoolDomain, DefaultBool, DefaultLoc, DefaultNull, DefaultNumber, DefaultUndef, NullDomain, NumDomain, RecencyAAddr, StrDomain, StringSet, UndefDomain, register }
import kr.ac.kaist.safe.nodes.cfg.{ CFG, CFGUtil }
import kr.ac.kaist.safe.util.StrOption
import kr.ac.kaist.safe.pointerAnalysis._

import scala.util.Try

case object AnalyzeWALA extends PhaseObj[CFG, WALAConfig, Double] {

  val name: String = "analyzerWALA"
  val help: String = "Analyze JavaScript source files using WALA."

  def apply(
    cfg: CFG,
    safeConfig: SafeConfig,
    config: WALAConfig
  ): Try[Double] = {

    register(
      config.AbsUndef,
      config.AbsNull,
      config.AbsBool,
      config.AbsNum,
      config.AbsStr,
      DefaultLoc,
    )
    var initSt = Initialize(cfg, config.jsModel)
    val cfgChild = new CFGUtil(cfg);

    val pointsTo = new WALAToSAFE(cfgChild, initSt)
    safeConfig.fileNames match {
      case List(file) => pointsTo.analyze(file)
      case files => pointsTo.analyze(files(0)) //TODO
    }
  }

  def defaultConfig: WALAConfig = WALAConfig()
  val options: List[PhaseOption[WALAConfig]] = List(
    ("out", StrOption((c, s) => c.outFile = Some(s)),
      "The WALA analysis result will be written to the outfile.")
  )
}
case class WALAConfig(
  var outFile: Option[String] = None,
  var AbsUndef: UndefDomain = DefaultUndef,
  var AbsNull: NullDomain = DefaultNull,
  var AbsBool: BoolDomain = DefaultBool,
  var AbsNum: NumDomain = DefaultNumber,
  var AbsStr: StrDomain = StringSet(0),
  var jsModel: Boolean = false,
) extends Config