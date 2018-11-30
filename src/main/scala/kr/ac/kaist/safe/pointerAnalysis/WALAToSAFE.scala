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

package kr.ac.kaist.safe.pointerAnalysis

import java.io.File

import com.ibm.wala.cast.ipa.callgraph.GlobalObjectKey
import com.ibm.wala.cast.ir.ssa.{ AstLexicalAccess }
import com.ibm.wala.cast.js.html.IncludedPosition
import com.ibm.wala.ipa.callgraph.propagation.{ InstanceKey, PointerAnalysis }
import com.ibm.wala.cast.ir.ssa.AstLexicalAccess.{ Access => LexicalAccess }

import scala.collection.JavaConverters._
import com.ibm.wala.cast.js.ipa.callgraph.JSCallGraphUtil
import com.ibm.wala.cast.js.test.JSCallGraphBuilderUtil
import com.ibm.wala.cast.js.translator.CAstRhinoTranslatorFactory
import com.ibm.wala.cast.js.types.JavaScriptMethods
import com.ibm.wala.cast.loader.{ AstFunctionClass, AstMethod }
import com.ibm.wala.cast.tree.CAstSourcePositionMap.Position
import com.ibm.wala.classLoader.{ IClass, IField }
import com.ibm.wala.ipa.callgraph.CGNode
import com.ibm.wala.ipa.callgraph.CallGraph
import com.ibm.wala.ssa.{ SSAAbstractInvokeInstruction, SSAInstruction, SSANewInstruction }
import com.ibm.wala.util.intset.OrdinalSet
import com.ibm.wala.util.strings.Atom
import kr.ac.kaist.safe.analyzer.domain.DefaultLoc.LocSet
import kr.ac.kaist.safe.util._
import kr.ac.kaist.safe.analyzer.domain._
import kr.ac.kaist.safe.nodes.cfg.{ CFGBlock, _ }

import scala.collection.immutable.{ HashMap, Map }
import scala.util.{ Success, Try }

class WALAToSAFE(cfgUtil: CFGUtil, initState: AbsState) {

  var pathName: String = null
  var analyzeFile = ""
  var callGraph: CallGraph = null
  var pa: PointerAnalysis[InstanceKey] = null

  type SourceLoc = (String, (Int, Int))
  type FunctionId = Int
  type Info = kr.ac.kaist.safe.util.Span
  type Node = (FunctionId, CFGBlock)
  type ClosureVariableLoc = (SourceLoc, (String, String))
  val quiet = true

  def analyze(fileName: String): Try[Double] = {

    val analysisfile = new File(fileName)
    val url = analysisfile.toURI().toURL()
    val translatorFactory = new CAstRhinoTranslatorFactory();
    JSCallGraphUtil.setTranslatorFactory(translatorFactory);

    var dir = url.getPath();
    var file = url.getFile();
    val split = dir.lastIndexOf('/');
    dir = dir.substring(0, split);
    file = file.substring(split + 1);
    pathName = System.getProperty("user.dir")
    analyzeFile = dir + "/" + file

    val start = System.nanoTime()
    val builder = JSCallGraphBuilderUtil.makeScriptCGBuilder(dir, file);
    callGraph = builder.makeCallGraph(builder.getOptions)
    pa = builder.getPointerAnalysis();

    val analysisTime = (System.nanoTime - start) / 1000000000.0
    println("* Time for WALA analysis(s) : " + analysisTime)

    makeHeap()
    Success(averagePointsTo)

  }

  def makeHeap(): Unit = {

    computeFilesOfFunctions()

    // traverse CallGraph
    // 1. computes function position
    computesFunctionInfo()

    // 2. computes lexical information
    traverseCallGraph()

    computesCGNodeToInstanceKey()

    traverseIR()

    //  1. Collect a creation source location of each instance key
    //  2. Generate WALA heap with out local/temp/lexical variable
    traverseInstanceKey()

    // Computes reachable functions in a program
    computesReachableFunctions()

    // Traverse CFG
    traverseSAFECFG()

    // 1. Collect Creation site : make a map from wala object to safe location
    computesWALAObjToSAFELoc()

    // 2. Computes Global Variables
    computesUserGlobalVariables()

    walaPointsToAverage()
  }

  var astFunctionSet: Iterable[CGNode] = null
  var syntheticFunctionSet: Set[CGNode] = null
  var jsFileSourceLocSet: Set[SourceLoc] = null

  var globalSourceLocMap = Map[SourceLoc, String]()
  var globalLexicalNameSet = Set[String]()

  var builtinMainCGNode = Set[CGNode]()
  var builtinFiles = Set[String]()

  def computeFilesOfFunctions(): Unit = {

    jsFileSourceLocSet = callGraph.getSuccNodes(callGraph.getFakeRootNode()).asScala.foldLeft(Set[SourceLoc]())((set, function) => {
      val position = function.getMethod().asInstanceOf[AstMethod].getSourcePosition()
      val functionKey = (position.getURL().getFile(), (position.getFirstOffset(), position.getLastOffset()))
      val lexicalName = function.getMethod().asInstanceOf[AstMethod].lexicalInfo().getScopingName()
      if (!position.getURL().getFile().endsWith("prologue.js") && !position.getURL().getFile().endsWith("preamble.js")) {
        globalSourceLocMap += (functionKey -> lexicalName)
        globalLexicalNameSet += lexicalName
        callGraph.getSuccNodes(function).asScala.foreach(mainFunction => {
          if (!mainFunction.getMethod.isSynthetic) {
            val mainPosition = mainFunction.getMethod().asInstanceOf[AstMethod].getSourcePosition()
            val mainFunctionKey = (mainPosition.getURL().getFile(), (mainPosition.getFirstOffset(), mainPosition.getLastOffset()))
            val mainLexicalName = mainFunction.getMethod().asInstanceOf[AstMethod].lexicalInfo().getScopingName()
            globalSourceLocMap += (mainFunctionKey -> mainLexicalName)
            globalLexicalNameSet += mainLexicalName
          }
        })
      } else {
        builtinMainCGNode += function
        builtinFiles += position.getURL().getFile()
      }
      set + functionKey
    })

    astFunctionSet = callGraph.asScala.filter(function => ((!function.getMethod().isSynthetic()) && (!builtinMainCGNode.contains(function))))
    syntheticFunctionSet = callGraph.asScala.filter(function => (function.getMethod().isSynthetic())).toSet - callGraph.getFakeRootNode()
  }

  def makeField(obj: IClass, fieldStr: String): IField = {
    val fieldStrAtom = Atom.findOrCreateUnicodeAtom(fieldStr)
    obj.getField(fieldStrAtom)
  }
  def getFunctionPosition(pos: Position): Position = {
    if (pos.isInstanceOf[IncludedPosition]) {
      val outerPos = pos.asInstanceOf[IncludedPosition].getIncludePosition
      val url = outerPos.getURL
      if (pos.getURL.getFile.startsWith(url.getFile)) {
        outerPos
      } else {
        pos
      }
    } else {
      pos
    }
  }
  def getPosition(pos: Position): Position = {
    pos
  }
  def computesFunctionInfo(): Unit = {
    var forInBodyNameSet = Set[String]()
    // add initial lexical string "" not to generate exception
    val lexicalNameSet = astFunctionSet.foldLeft(Set[String]())((set, f) => {
      set + f.getMethod().asInstanceOf[AstMethod].lexicalInfo().getScopingName()
    })
    // source locations for .js file in WALA

    // computes functionNameToAstMethod map
    callGraph.asScala.foreach(function => {
      if (function.getMethod().isSynthetic()) {
        val functionName = function.getMethod().getReference().getDeclaringClass().getName().toString()
        // apply-call will not be called by builtin functions
        if (functionName.equals("Lprologue.js/Function_prototype_apply")) {
          reachableBuiltinFunctionNameSet += functionName
        } else if (functionName.equals("Lprologue.js/Function_prototype_call")) {
          reachableBuiltinFunctionNameSet += functionName
        } else if (!functionName.contains("/")) {
          // Set reachable function for builtin constructor function
          // replace function name to be matched in SAFE
          functionName match {
            case "LObject" | "LArray" | "LFunction" |
              "LNumberObject" | "LStringObject" | "LRegExpObject" =>
              reachableBuiltinFunctionNameSet += functionName
            case _ =>
              if (!quiet)
                println(" * No builtin function name mapping to SAFE " + functionName)
          }
        } else {
          // other synthetic functions are not called in SAFE
          // we do not need to compute it
        }
      } else {
        val astMethod = function.getMethod().asInstanceOf[AstMethod]
        //        val function_pos = getPosition(ast_method.getSourcePosition())
        val functionPos = getFunctionPosition(astMethod.getSourcePosition())
        val functionName = astMethod.lexicalInfo().getScopingName()
        //        println(" functionName : " + functionName + " \t\t : " + function_pos)
        // if function_pos == null, the function is generated by for-in
        if (functionPos != null) {
          // for builtin model function in WALA
          //TODO
          // computes function source location
          // function positions do not need modification
          val functionSourceLoc = (functionPos.getURL().getFile(), (functionPos.getFirstOffset(), functionPos.getLastOffset()))
          reachableFunctionSourceLocSet += functionSourceLoc
          ////////////////////////////////////////////////////////////////////////////////////////////////////
          // XXX:  Debug
          if (lexicalNameToFunctionSourceLocMap.contains(functionName)) {
            // ignore if ast_method and stored ast_method are the same
            if (lexicalNameToFunctionSourceLocMap(functionName) != functionSourceLoc) {
              if (!quiet) {
                println("Position : " + lexicalNameToFunctionSourceLocMap(functionName))
                println("\t target: " + functionSourceLoc)
              }
              throw new InternalError("functionNameToAstMethod is not one-to-one map : WALATyping.computesFunctionPosition : " + functionName)
            }
          }
          ////////////////////////////////////////////////////////////////////////////////////////////////////
          lexicalNameToFunctionSourceLocMap += functionName -> functionSourceLoc

        } else {
          // if function_pos is null, this function is a body of for-in loop
          forInBodyNameSet += functionName
        }
      }
    })
    // Eliminate .js file source location
    reachableFunctionSourceLocSet --= jsFileSourceLocSet

    val functionNameSet = lexicalNameToFunctionSourceLocMap.keySet
    // computes functionNameToAstMethod map for for-in-body
    forInBodyNameSet.foreach(forInBody => {
      val functionName = getFunctionName(forInBody, functionNameSet)
      lexicalNameToFunctionSourceLocMap += forInBody -> lexicalNameToFunctionSourceLocMap(functionName)
      forinLexicalNameToLexicalNameMap += forInBody -> functionName
    })
  }

  def ordinalSetToString(pointsTo: OrdinalSet[InstanceKey]): String = {
    var str = ""
    pointsTo.asScala.foreach(ikey => {
      val ind = pa.getInstanceKeyMapping().getMappedIndex(ikey)
      str += ("#" + ind + ", ")
    })
    str
  }

  var callerSourceLocOfFunctionApply: Set[Position] = Set()
  var builtinCallerOfFunctionApply: Set[String] = Set()
  var callerSourceLocOfSpecialFunctionMap: Map[String, Set[Position]] = Map()
  var builtinCallerOfSpecialFunctionMap: Map[String, Set[String]] = Map()
  var callerSourceLocOfFunctionCall: Set[Position] = Set()
  var builtinCallerOfFunctionCall: Set[String] = Set()

  def traverseCallGraph(): Unit = {
    val lexicalNameSet = astFunctionSet.foldLeft(Set[String]())((set, f) => {
      set + f.getMethod().asInstanceOf[AstMethod].lexicalInfo().getScopingName()
    })

    // source locations for .js file in WALA
    val jsFileSourceLocSet = callGraph.getSuccNodes(callGraph.getFakeRootNode()).asScala.foldLeft(Set[SourceLoc]())((set, function) => {
      val position = getPosition(function.getMethod().asInstanceOf[AstMethod].getSourcePosition())
      val functionKey = (position.getURL().getFile(), (position.getFirstOffset(), position.getLastOffset()))
      set + functionKey
    })
    // computes functionNameToAstMethod map
    callGraph.asScala.foreach(function => {
      if (function.getMethod().isSynthetic()) {
        val functionName = function.getMethod().getReference().getDeclaringClass().getName().toString()
        // TODO : for apply, and call function
        // apply-call will not be called by builtin functions
        if (functionName.equals("Lprologue.js/Function_prototype_apply")) {
          // Get caller site of function.prototype.apply
          // Function.prototype.apply cannot be used as constructor
          // callersite will be a call node that generates lexical information in SAFE
          val (callsite1, callsite2) = getAstCallerFunction(function)
          callerSourceLocOfFunctionApply ++= callsite1
          lexicalNameToOuterStringMap += functionName -> ""
          builtinCallerOfFunctionApply ++= callsite2.foldLeft(Set[String]())((sset, caller) => { sset + caller.getMethod().getReference().getDeclaringClass().getName().toString() })

          // FIXED to compute caller source loc for Function.prototype.apply
          callerSourceLocOfSpecialFunctionMap.get(functionName) match {
            case Some(callsiteSet) => callerSourceLocOfSpecialFunctionMap += functionName -> (callsiteSet ++ callsite1)
            case None => callerSourceLocOfSpecialFunctionMap += functionName -> callsite1
          }

          builtinCallerOfSpecialFunctionMap += functionName -> callsite2.foldLeft(Set[String]())((sset, caller) => { sset + caller.getMethod().getReference().getDeclaringClass().getName().toString() })
        } else if (functionName.equals("Lprologue.js/Function_prototype_call")) {
          lexicalNameToOuterStringMap += functionName -> ""
          // Get caller site of function.prototype.call
          val (callsite1, callsite2) = getAstCallerFunction(function)
          callerSourceLocOfFunctionCall ++= callsite1
          builtinCallerOfFunctionCall ++= callsite2.foldLeft(Set[String]())((sset, caller) => { sset + caller.getMethod().getReference().getDeclaringClass().getName().toString() })

          // FIXED to compute caller source loc for Function.prototype.apply
          callerSourceLocOfSpecialFunctionMap.get(functionName) match {
            case Some(callsiteSet) => callerSourceLocOfSpecialFunctionMap += functionName -> (callsiteSet ++ callsite1)
            case None => callerSourceLocOfSpecialFunctionMap += functionName -> callsite1
          }
          builtinCallerOfSpecialFunctionMap += functionName -> callsite2.foldLeft(Set[String]())((sset, caller) => { sset + caller.getMethod().getReference().getDeclaringClass().getName().toString() })

          // Function.prototype.call needs callee's arguments object
          val calleeset = callGraph.getSuccNodes(function)
          calleeset.asScala.foreach(callee => {
            val pointsTo = getArgumentsObject(callee).asInstanceOf[OrdinalSet[InstanceKey]]
            builtinCalleeArgumentsSet = OrdinalSet.unify(builtinCalleeArgumentsSet, pointsTo)
          })
        } else {
          // other synthetic functions are not called in SAFE
        }
      } else {
        val astMethod = function.getMethod().asInstanceOf[AstMethod]
        val functionPos = getPosition(astMethod.getSourcePosition())
        val functionName = astMethod.lexicalInfo().getScopingName()
        // add lexical info for function declaration
        if (functionPos != null)
          addLexicalInfo(functionPos, functionName)
        val calleeName =
          if (functionPos != null) {
            functionName
          } else {
            forinLexicalNameToLexicalNameMap(functionName)
          }
        // for ast functions, computes lexical outer property
        // NOTE : function name and lexical name are the same
        // use callee_name for actual callee function name
        val (callsite1, callsite2) = getAstCallerFunction(function)
        // add position that lexical name is generated in SAFE
        callsite1.foreach(callerPosition => addLexicalInfo(callerPosition, calleeName))
        callsite2.foreach(builtinCaller => {
          val forinName = builtinCaller.getMethod().getDeclaringClass().getName().toString()
          // get actual caller_name in ast function
          val callerName = forinLexicalNameToLexicalNameMap.get(forinName) match {
            case Some(callerName) => callerName
            case None => forinName
          }
          //////////////////////////////////////////
          addLexicalBuiltinInfo(callerName, calleeName)
        })

        // a function generated by for-in loop could have more than one call-context
        if (!lexicalNameToOuterStringMap.contains(functionName)) {
          // compute @outer for lexical information
          // @outer lexical string only can be one in WALA
          val outerString = getLexicalOuterString(lexicalNameSet, functionName)
          lexicalNameToOuterStringMap += functionName -> outerString
        }
      }
    })
  }

  /* To find out lexical outer information in SAFE,
 * we use a function  name(lexical scope string) by using separator '/'
 */
  def getLexicalOuterString(lexicalNameSet: Set[String], functionName: String): String = {
    var index = functionName.lastIndexOf("/")
    // if index is -1, lexical name is fakeroot
    var lexicalName =
      if (index < 0) ""
      else functionName.substring(0, index)
    lexicalName
  }

  def getAstCallerFunction(function: CGNode): (Set[Position], Set[CGNode]) = {
    var callerFunctionSet = Set[Position]()
    var callerBuiltinFunctionSet = Set[CGNode]()
    val callers = callGraph.getPredNodes(function).asScala
    callers.foreach(caller => {
      if (caller.getMethod().isSynthetic()) {
        val functionName = caller.getMethod().getReference().getDeclaringClass().getName().toString()
        // if caller is Function.prototype.apply/call, it is okay.
        if (functionName.equals("Lprologue.js/Function_prototype_apply") || functionName.equals("Lprologue.js/Function_prototype_call")) {
          callerBuiltinFunctionSet += caller
        } // if not, lookup caller of caller
        else {
          callGraph.getPredNodes(caller).asScala.foreach(callerOfCaller => {
            callerFunctionSet ++= getCallerPosition(callerOfCaller, caller).map(v => { val (v1, v2) = v; v2 })
          })
        }
      } else {
        callerFunctionSet ++= getCallerPosition(caller, function).map(v => { val (v1, v2) = v; v2 })
      }
    })
    (callerFunctionSet, callerBuiltinFunctionSet)
  }

  def getCallerPositionSet(function: CGNode): (Set[(String, Position)], Set[String]) = {
    var creationPositions = Set[(String, Position)]()
    var creationLexicalNames = Set[String]()
    val callerFunctions = callGraph.getPredNodes(function).asScala
    callerFunctions.foreach(caller => {
      // if caller is not synthetic function,
      // callsite is creation site
      if (!caller.getMethod().isSynthetic()) {
        val functionName = caller.getMethod().getReference().getDeclaringClass().getName().toString()
        creationPositions ++= getCallerPosition(caller, function)
        creationLexicalNames += functionName
      } // if caller is synthetic function,
      // lookup caller_of_caller
      // usually it happens when a caller is constructor?
      else {
        // caller is Function.prototype.apply-call
        val functionName = caller.getMethod().getReference().getDeclaringClass().getName().toString()
        // if caller is Function.prototype.apply/call, it is okay.
        if (functionName.equals("Lprologue.js/Function_prototype_apply") || functionName.equals("Lprologue.js/Function_prototype_call")) {
          creationLexicalNames += functionName
        } else {
          val callerOfCallerSet = callGraph.getPredNodes(caller).asScala
          callerOfCallerSet.foreach(callerOfCaller => {
            ////////////////////////////////
            // the same as caller check
            if (!callerOfCaller.getMethod().isSynthetic()) {
              creationPositions ++= getCallerPosition(callerOfCaller, caller)
            } else {
              throw new InternalError(" * We could not find source location : Impossible case in : WALATyping.getCallerPositionSet")
            }
            ////////////////////////////////
          })
        }
      }
    })
    (creationPositions, creationLexicalNames)
  }

  def getCallerPosition(caller: CGNode, callee: CGNode): Set[(String, Position)] = {
    var creationPositions = Set[(String, Position)]()
    val functionName = caller.getMethod().getReference().getDeclaringClass().getName().toString()
    val callSites = callGraph.getPossibleSites(caller, callee).asScala
    callSites.foreach(callsite => {
      val callIndices = caller.getIR().getCallInstructionIndices(callsite).intIterator()
      while (callIndices.hasNext()) {
        val callIndex = callIndices.next()
        val position = getPosition(caller.getMethod().asInstanceOf[AstMethod].getSourcePosition(callIndex))
        creationPositions += ((functionName, position))
      }
    })
    creationPositions
  }

  /* Generate lexical information
 * that maps to environment record.
 * NOTE: WALA generates environment record for each function
 *       SAFE generates environment record for each call-site
 */

  // Source Position to generated lexical information
  var lexicalNameLocMap: Map[Position, Set[String]] = Map()
  // a map from lexical name to its caller functino source location
  var lexicalNameToSourceLocMap: Map[String, Set[SourceLoc]] = Map()
  // Lexical string will be generated in caller site by SAFE
  // lexical outer should be only one
  var lexicalNameToOuterStringMap = Map[String, String]()

  // a map from lexical name to its function source location : 1 to 1 mapping
  // WALA -> Source Loc for lexical name
  var lexicalNameToFunctionSourceLocMap = Map[String, SourceLoc]()
  var forinLexicalNameToLexicalNameMap = Map[String, String]()

  def addLexicalInfo(position: Position, lexicalStr: String): Unit = {
    val startOffset = eventFunctionWALAStartOffSet(position)

    lexicalNameLocMap.get(position) match {
      case Some(lexicalStrSet) => lexicalNameLocMap += position -> (lexicalStrSet + lexicalStr)
      case None => lexicalNameLocMap += position -> Set(lexicalStr)
    }

    val sourceLoc = (position.getURL().getFile(), (position.getFirstOffset() + startOffset, position.getLastOffset() + startOffset))
    lexicalNameToSourceLocMap.get(lexicalStr) match {
      case Some(sourceLocSet) => lexicalNameToSourceLocMap += lexicalStr -> (sourceLocSet + sourceLoc)
      case None => lexicalNameToSourceLocMap += lexicalStr -> Set(sourceLoc)
    }
  }

  var lexicalNameToBuiltinLexicalNameMap: Map[String, Set[String]] = Map()
  // builtin_function can be both a Function.prototype.apply and call
  // lexicalStr is a callee function
  // Therefore, callsite of lexicalStr is Function.prototype.apply-call.

  def addLexicalBuiltinInfo(builtinFunctionCallerStr: String, lexicalStr: String): Unit = {
    lexicalNameToBuiltinLexicalNameMap.get(lexicalStr) match {
      case Some(callerStrSet) => lexicalNameToBuiltinLexicalNameMap += lexicalStr -> (callerStrSet + builtinFunctionCallerStr)
      case None => lexicalNameToBuiltinLexicalNameMap += lexicalStr -> Set(builtinFunctionCallerStr)
    }
  }

  // only for Function.prototype.call function.
  var builtinCalleeArgumentsSet = OrdinalSet.empty.asInstanceOf[OrdinalSet[InstanceKey]]

  def getArgumentsInstr(function: CGNode): SSAInstruction = {
    for (newIndex <- 0 to function.getIR().getInstructions().length) {
      val newInst = function.getIR().getInstructions()(newIndex)
      if (newInst.isInstanceOf[SSANewInstruction]) {
        return newInst
      }
    }
    null: SSAInstruction
  }
  def getArgumentsObject(function: CGNode): OrdinalSet[InstanceKey] = {
    val argumentsInst = getArgumentsInstr(function)
    val argSsa = argumentsInst.getDef()
    val argPKey = pa.getHeapModel().getPointerKeyForLocal(function, argSsa)
    if (argPKey != null)
      pa.getPointsToSet(argPKey).asInstanceOf[OrdinalSet[InstanceKey]]
    else OrdinalSet.empty()
  }

  def getFunctionName(forInBody: String, functionNameSet: Set[String]): String = {
    var function = forInBody
    while (!functionNameSet.contains(function)) {
      function = function.substring(0, function.lastIndexOf("/"))
    }
    function
  }

  def computesSSAPointsTo(function: CGNode, ssaNum: Int): OrdinalSet[InstanceKey] = {
    val pointerKey = pa.getHeapModel().getPointerKeyForLocal(function, ssaNum)
    pa.getPointsToSet(pointerKey)
  }

  def computesFieldPointsTo(obj: InstanceKey, field: IField): (String, OrdinalSet[InstanceKey]) = {
    val fieldKey = pa.getHeapModel().getPointerKeyForInstanceField(obj, field)
    if (fieldKey == null) {
      (null, null: OrdinalSet[InstanceKey])
    } else {
      try {
        val pointsTo = pa.getPointsToSet(fieldKey)
        (field.getName().toString(), pointsTo.asInstanceOf[OrdinalSet[InstanceKey]])
      } catch {
        case _: Throwable =>
          println("exception with field_key : " + fieldKey)
          val pointsTo = pa.getPointsToSet(fieldKey)
          (field.getName().toString(), pointsTo.asInstanceOf[OrdinalSet[InstanceKey]])
      }
    }
  }

  // Find the source location of creation site
  private def addCreationSite(fid: FunctionId, info: Info, asite: AllocSite, map: Map[SourceLoc, Set[Loc]]): Map[SourceLoc, Set[Loc]] = {
    val loc = Loc(asite)
    val startOffset = eventFunctionSAFEStartOffSet(fid)
    val key = (info.fileName, (info.begin.offset + startOffset, info.end.offset + startOffset))
    map.get(key) match {
      case Some(aSet) => map + (key -> (aSet + loc))
      case None => map + (key -> Set(loc))
    }
  }
  var functionToInstanceKeyMap = Map[CGNode, OrdinalSet[InstanceKey]]()
  /*
 * `1` ssa variable in each CGNode contains its function instance key
 */
  def computesCGNodeToInstanceKey(): Unit = {
    callGraph.asScala.foreach(function => {
      val functionPKey = pa.getHeapModel().getPointerKeyForLocal(function, 1)
      val functionIkeySet = pa.getPointsToSet(functionPKey).asInstanceOf[OrdinalSet[InstanceKey]]
      if (functionToInstanceKeyMap.contains(function)) throw new InternalError("* Duplicated CGNode...")
      functionToInstanceKeyMap += function -> functionIkeySet
    })
  }

  // Variable Maps
  var localVariableMap: Map[SourceLoc, Map[String, OrdinalSet[InstanceKey]]] = Map()
  var argumentVariableMap: Map[SourceLoc, Map[String, OrdinalSet[InstanceKey]]] = Map()
  var ssaVariableMap: Map[SourceLoc, OrdinalSet[InstanceKey]] = Map()
  var lexicalVariableMap: Map[ClosureVariableLoc, OrdinalSet[InstanceKey]] = Map()

  def addLocalVariable(functionKey: SourceLoc, variableArray: Array[String], newPointsTo: OrdinalSet[InstanceKey]): Unit = {
    variableArray.foreach(variable => {
      var flag = false
      if (variable.equals("____eeee____")) {
        flag = true
        println("* Function_key = " + functionKey)
      }
      localVariableMap.get(functionKey) match {
        case Some(variableMap) =>
          val newVariableMap =
            variableMap.get(variable) match {
              case Some(pointsTo) => variableMap + (variable -> OrdinalSet.unify(pointsTo, newPointsTo))
              case None => variableMap + (variable -> newPointsTo)
            }
          if (flag) {
            println(" add points_to1 : " + ordinalSetToString(newPointsTo))
          }
          localVariableMap += functionKey -> newVariableMap
        case None =>
          if (flag) {
            println(" add points_to2 : " + ordinalSetToString(newPointsTo))
          }
          localVariableMap += functionKey -> Map(variable -> newPointsTo)
      }
    })
  }
  def AddArgVaraible(ssaKey: SourceLoc, index: Int, newPointsTo: OrdinalSet[InstanceKey]): Unit = {
    argumentVariableMap.get(ssaKey) match {
      case Some(map) =>
        map + (index.toString -> newPointsTo)
        argumentVariableMap += ssaKey -> map
      case None =>

        argumentVariableMap += ssaKey -> Map(index.toString -> newPointsTo)
    }
  }

  def addSSAVariable(ssaKey: SourceLoc, newPointsTo: OrdinalSet[InstanceKey]): Unit = {
    ssaVariableMap.get(ssaKey) match {
      case Some(pointsTo) =>
        ssaVariableMap += ssaKey -> OrdinalSet.unify(pointsTo, newPointsTo)
      case None =>
        ssaVariableMap += ssaKey -> newPointsTo
    }
  }

  def addVariableMap(sourceKey: SourceLoc, newPointsTo: OrdinalSet[InstanceKey], map: Map[SourceLoc, OrdinalSet[InstanceKey]]): Map[SourceLoc, OrdinalSet[InstanceKey]] = {
    map.get(sourceKey) match {
      case Some(pointsTo) =>
        map + (sourceKey -> OrdinalSet.unify(pointsTo, newPointsTo))
      case None =>
        map + (sourceKey -> newPointsTo)
    }
  }
  /*
 * Because AstLexicalInstruction has different way to make ssa variable,
 * we need to compute points-to for lexical variable
 */
  def addClosureVariable(function: CGNode, functionKey: SourceLoc, closureArray: Array[LexicalAccess]): Unit = {
    closureArray.foreach(variable => {
      // computes points-to of a lexical variable
      val ssaVn = variable.valueNumber
      val newPointsTo = computesSSAPointsTo(function, ssaVn).asInstanceOf[OrdinalSet[InstanceKey]]

      // make a closure key
      val closureKey = (functionKey, (variable.variableDefiner, variable.variableName))
      lexicalVariableMap.get(closureKey) match {
        case Some(pointsTo) =>
          lexicalVariableMap += closureKey -> OrdinalSet.unify(pointsTo, newPointsTo)
        case None =>
          lexicalVariableMap += closureKey -> newPointsTo
      }
    })
  }
  // Variable maps for call instruction
  var callFunctionVariableMap: Map[SourceLoc, OrdinalSet[InstanceKey]] = Map()
  var callThisVariableMap: Map[SourceLoc, OrdinalSet[InstanceKey]] = Map()
  var callReturnVariableMap: Map[SourceLoc, OrdinalSet[InstanceKey]] = Map()
  var callFunctionVariableCacheMap: Map[SourceLoc, LocSet] = Map()
  var callThisVariableCacheMap: Map[SourceLoc, LocSet] = Map()
  var callReturnVariableCacheMap: Map[SourceLoc, LocSet] = Map()

  // 1. store function that generated by for-in
  var forInFunctionSet: Set[InstanceKey] = Set()
  var forInArgumentsSet: Set[InstanceKey] = Set()

  /*
* Traverse IR to collect information that have source location
* 1. Points-to for Local, Temporary variables
* 2. prototype property of each callsite for constructor functions
*/
  var callPrototypeVariableMap = Map[SourceLoc, OrdinalSet[InstanceKey]]()
  val printIR = false

  def traverseIR(): Unit = {
    astFunctionSet.foreach(function => {
      // for actual function
      val astMethod = function.getMethod().asInstanceOf[AstMethod]
      val functionName = astMethod.lexicalInfo().getScopingName()
      val startOffset = eventFunctionWALAStartOffSet(functionName)
      val ir = function.getIR()

      // Do not need to traverse in builtin function...
      // also a function that generated by for-in loop body
      val astPosition = getPosition(astMethod.getSourcePosition())
      if ((astPosition != null && !this.builtinFiles.contains(astPosition.getURL().getFile())) || astPosition == null) {
        // 1. Get Function name
        //        val function_name = ast_method.lexicalInfo().getScopingName()
        // 2. Get Function position
        //    if function_pos is null, this function is generated by correlation tracking.(for-in)
        // 3. generate function position key
        val functionKey = lexicalNameToFunctionSourceLocMap(functionName)

        // 4. parameter variables : to local variable
        for (ssaVn <- 1 to astMethod.getNumberOfParameters()) {
          val paramNum = astMethod.getNumberOfParameters
          val parameterNameArray = ir.getLocalNames(0, ssaVn)
          // make pointer key to get points-to for local variable

          val newPointsTo = computesSSAPointsTo(function, ssaVn).asInstanceOf[OrdinalSet[InstanceKey]]
          addLocalVariable(functionKey, parameterNameArray, newPointsTo)
        }
        /////////////////////////////////////////////////////////////
        // for each instruction *******
        for (ind <- 0 until ir.getInstructions().length) {
          val inst = ir.getInstructions()(ind)
          val instPosition = getPosition(astMethod.getSourcePosition(ind))
          // inst_position could be null when a function is generated by for-in body
          if (inst != null && instPosition != null) {

            // 5. closure variables write in instruction
            if (inst.isInstanceOf[AstLexicalAccess]) {
              val lexicalReadInst = inst.asInstanceOf[AstLexicalAccess]
              addClosureVariable(function, functionKey, lexicalReadInst.getAccesses())
            } else if (inst.isInstanceOf[SSAAbstractInvokeInstruction]) {
              // 8. Collect callee, this, and return values
              val callInst = inst.asInstanceOf[SSAAbstractInvokeInstruction]

              // it is easy to compute invoke or consturct instruction.
              // it is compatible to dispatch instruction
              val functionPointsTo =
                callGraph.getPossibleTargets(function, callInst.getCallSite()).asScala.foldLeft(OrdinalSet.empty.asInstanceOf[OrdinalSet[InstanceKey]])((set, function) => {
                  OrdinalSet.unify(set, functionToInstanceKeyMap(function))
                })
              val instKey = (instPosition.getURL().getFile(), (instPosition.getFirstOffset() + startOffset, instPosition.getLastOffset() + startOffset))
              // 9. For constructor calls, store prototype property of a function
              if (callInst.getCallSite().getDeclaredTarget().equals(JavaScriptMethods.ctorReference)) {
                val prototypePointsTo = functionPointsTo.asScala.foldLeft(OrdinalSet.empty().asInstanceOf[OrdinalSet[InstanceKey]])((set, functionIkey) => {
                  val prototypeField = makeField(functionIkey.getConcreteType(), "prototype")
                  val prototypePkey = pa.getHeapModel().getPointerKeyForInstanceField(functionIkey, prototypeField)
                  val prototypePointsTo =
                    // for constructor, function may points to NULL
                    if (prototypePkey == null) {
                      OrdinalSet.empty().asInstanceOf[OrdinalSet[InstanceKey]]
                    } else {
                      pa.getPointsToSet(prototypePkey).asInstanceOf[OrdinalSet[InstanceKey]]
                    }
                  OrdinalSet.unify(set, prototypePointsTo)
                })
                callPrototypeVariableMap += instKey -> prototypePointsTo
              }
              // use 0 is for function
              // use 1 is for this value
              // return is for return value
              // use 1 may not exist in call instruction : 'new f()'
              // collect information in call/constructor

              // for normal function call
              if (callInst.getNumberOfUses() > 1) {
                val numberOfUse = callInst.getNumberOfUses()
                val ssaThisVn = callInst.getUse(1)
                val ssaReturnVn = callInst.getReturnValue(0)
                val thisPointsTo = computesSSAPointsTo(function, ssaThisVn).asInstanceOf[OrdinalSet[InstanceKey]]
                val returnPointsTo = computesSSAPointsTo(function, ssaReturnVn).asInstanceOf[OrdinalSet[InstanceKey]]

                callFunctionVariableMap = addVariableMap(instKey, functionPointsTo, callFunctionVariableMap)
                callThisVariableMap = addVariableMap(instKey, thisPointsTo, callThisVariableMap)
                callReturnVariableMap = addVariableMap(instKey, returnPointsTo, callReturnVariableMap)
                // return value could be reassigned to variable
                addSSAVariable(instKey, returnPointsTo)
                for (i <- 3 to callInst.getNumberOfUses()) {
                  val argVn = callInst.getUse(i - 1)
                  val argPointsTo = computesSSAPointsTo(function, argVn)
                  AddArgVaraible(instKey, i - 3, argPointsTo)
                }
              } // for constructor call
              else {
                val ssaReturnVn = callInst.getReturnValue(0)
                val returnPointsTo = computesSSAPointsTo(function, ssaReturnVn).asInstanceOf[OrdinalSet[InstanceKey]]
                callFunctionVariableMap = addVariableMap(instKey, functionPointsTo, callFunctionVariableMap)
                callReturnVariableMap = addVariableMap(instKey, returnPointsTo, callReturnVariableMap)
                // return value could be reassigned to variable
                addSSAVariable(instKey, returnPointsTo)
              }
            }
            /////////////////////////////////////////////////////////////
            //            else {
            val ssaDefVn = inst.getDef()
            if (ssaDefVn >= 0) {
              val newPointsTo = computesSSAPointsTo(function, ssaDefVn).asInstanceOf[OrdinalSet[InstanceKey]]

              // 6. local variables in instruction
              val localVariableArray = ir.getLocalNames(0, ssaDefVn)
              addLocalVariable(functionKey, localVariableArray, newPointsTo)
              /////////////////////////////////////////////////////////////

              // 7. ssa variables in instruction
              val ssaKey = (instPosition.getURL().getFile(), (instPosition.getFirstOffset() + startOffset, instPosition.getLastOffset() + startOffset))
              addSSAVariable(ssaKey, newPointsTo)
              /////////////////////////////////////////////////////////////
            }
          }
        }
      } else {
        //  TODO
        // builtin functions not considered
      }
    })
    // TODO
    // for builtin function, we need to store this variable and arguments variable
    // they only are used as a constructor in WALA
    syntheticFunctionSet.foreach(function => {
      val functionName = function.getMethod().getReference().getDeclaringClass().getName().toString()
      // function prototype apply-call
      if (functionName.endsWith("prologue.js/Function_prototype_apply") || functionName.endsWith("prologue.js/Function_prototype_call")) {
        // points_to `this` v2 is actual `this` value
        val thisPkey = pa.getHeapModel().getPointerKeyForLocal(function, 2)
        val thisPointsTo = pa.getPointsToSet(thisPkey).asInstanceOf[OrdinalSet[InstanceKey]]

        // make arguments in builtin function
        // index 3 will be `this` value callee of apply
        for (index <- 3 to function.getIR().getNumberOfParameters()) {
          val num = function.getIR().getNumberOfParameters()
          val argIPkey = pa.getHeapModel().getPointerKeyForLocal(function, index)
          val argIPointsTo = pa.getPointsToSet(argIPkey).asInstanceOf[OrdinalSet[InstanceKey]]
          // Generate arguments objec for builtin function apply-call
          val (callerPosPairSet, argumentsStringSet) = getCallerPositionSet(function)
          callerPosPairSet.foreach(argumentsPositionPair => {
            val (callerFunctionName, argumentsPosition) = argumentsPositionPair
            val callerStartOffset = eventFunctionWALAStartOffSet(callerFunctionName)
            val objKey = (argumentsPosition.getURL().getFile(), (argumentsPosition.getFirstOffset() + callerStartOffset, argumentsPosition.getLastOffset() + callerStartOffset))
            addBuiltinArgumentsObject(objKey, index - 3, argIPointsTo)
          })
          //addBuiltinArgumentsObject(functionName, index - 3, argIPointsTo)
        }

      } // ignore source based function
      else if (!functionName.contains(".js")) {
        // ignore it
      }

    })
  }

  // map from creation site to obj in WALA
  var argumentsCreationLocMap: Map[SourceLoc, Set[InstanceKey]] = Map()
  var argumentsCreationLexicalNameMap: Map[String, Set[InstanceKey]] = Map()
  var objCreationLocMap: Map[SourceLoc, Set[InstanceKey]] = Map()
  var functionCreationLocMap: Map[SourceLoc, Set[InstanceKey]] = Map()
  var functionPrototypeCreationLocMap: Map[SourceLoc, Set[InstanceKey]] = Map()

  def addFunctionObjCreationSite(objKey: SourceLoc, obj: InstanceKey): Unit = {
    functionCreationLocMap.get(objKey) match {
      case Some(pointsTo) => functionCreationLocMap += objKey -> (pointsTo + obj)
      case None => functionCreationLocMap += objKey -> Set(obj)
    }
  }

  def addFunctionProtoObjCreationSite(objKey: SourceLoc, obj: InstanceKey): Unit = {
    functionPrototypeCreationLocMap.get(objKey) match {
      case Some(pointsTo) => functionPrototypeCreationLocMap += objKey -> (pointsTo + obj)
      case None => functionPrototypeCreationLocMap += objKey -> Set(obj)
    }
  }

  def addArgumentsObjCreationSite(objKey: SourceLoc, obj: InstanceKey): Unit = {
    argumentsCreationLocMap.get(objKey) match {
      case Some(pointsTo) => argumentsCreationLocMap += objKey -> (pointsTo + obj)
      case None => argumentsCreationLocMap += objKey -> Set(obj)
    }
  }

  // builtin function name -> (arg index -> points_to)
  var builtinArgumentsObject = Map[SourceLoc, Map[Int, OrdinalSet[InstanceKey]]]()
  def addBuiltinArgumentsObject(objkey: SourceLoc, index: Int, newPointsTo: OrdinalSet[InstanceKey]): Unit = {
    builtinArgumentsObject.get(objkey) match {
      case Some(map) =>
        val newMap =
          map.get(index) match {
            case Some(prevPointsTo) => map + (index -> OrdinalSet.unify(prevPointsTo, newPointsTo))
            case None => map + (index -> newPointsTo)
          }
        builtinArgumentsObject += objkey -> newMap
      case None =>
        builtinArgumentsObject += objkey -> Map(index -> newPointsTo)
    }
  }

  def addObjCreationSite(objKey: SourceLoc, obj: InstanceKey): Unit = {
    objCreationLocMap.get(objKey) match {
      case Some(pointsTo) => objCreationLocMap += objKey -> (pointsTo + obj)
      case None => objCreationLocMap += objKey -> Set(obj)
    }
  }
  // each function has only one arguments object in WALA? NO! Builtin function may be generated more than twice
  var lexicalNameToArguments = Map[String, Set[InstanceKey]]()
  var safeFunctionIdToArguments = Map[FunctionId, Set[InstanceKey]]()

  // builtin instancekey -> Function Name should be one-to-one map
  var builtinFunctionObjectToName = Map[InstanceKey, String]()

  var eventLoopArgumentsWALA = Set[InstanceKey]()

  def isArgumentsInstr(function: CGNode, index: Int): Boolean = {
    for (newIndex <- 0 until function.getIR().getInstructions().length) {
      val newInst = function.getIR().getInstructions()(newIndex)
      if (newInst.isInstanceOf[SSANewInstruction]) {
        return (index == newIndex)
      }
    }
    false
  }

  // special object for global
  var globalObject: InstanceKey = null
  def traverseInstanceKey(): Unit = {
    //////////
    var functionInstanceKey = Set[InstanceKey]()
    //////////
    pa.getInstanceKeys().asScala.foreach(ikey => {

      if (pa.getInstanceKeyMapping().getMappedIndex(ikey) == 1173 || pa.getInstanceKeyMapping().getMappedIndex(ikey) == 1174) {

        val creationSites = ikey.getCreationSites(callGraph).asScala
        println("creation site for object #" + pa.getInstanceKeyMapping().getMappedIndex(ikey) + " size : " + creationSites.size)
        println("\t ikey : " + ikey)
        creationSites.foreach(creationSite => {
          val (function, newsiteRefer) = (creationSite.fst, creationSite.snd)
          println("\t ir(inst : " + newsiteRefer.getProgramCounter() + ")\n" + function.getIR())
        })
      }

      if (ikey.isInstanceOf[GlobalObjectKey]) {
        globalObject = ikey

      } else {
        val obj = ikey.getConcreteType()
        // function object has source position itself
        if (obj.isInstanceOf[AstFunctionClass]) {
          // Store functionInstanceKey to make a map from CGNode to InstanceKey
          functionInstanceKey += ikey

          ////////////////////
          val functionPos = getPosition(obj.asInstanceOf[AstFunctionClass].getSourcePosition())
          // function_pos is null if ikey is a function generated by for-in loop
          if (functionPos != null) {
            // make a map from Builtin Function InstanceKey to Builtin Function Name
            if (builtinFiles.contains(functionPos.getURL().getFile())) {
              val functionObj = obj.asInstanceOf[AstFunctionClass]
              builtinFunctionObjectToName += ikey -> functionObj.getName().toString()
              builtinFunctionNameSet += functionObj.getName().toString()
            }
            ////////////////
            val objKey = (functionPos.getURL().getFile(), (functionPos.getFirstOffset(), functionPos.getLastOffset()))
            addFunctionObjCreationSite(objKey, ikey)
            // lookup prototype property of ikey to get prototype object that generated in the same CGNode
            val functionCGNode = ikey.getCreationSites(callGraph).asScala.map(f => f.fst)
            val prototypeField = makeField(obj, "prototype")
            val prototypePointsToPair = computesFieldPointsTo(ikey, prototypeField)
            val (fieldName, prototypePointsToSet) = prototypePointsToPair
            prototypePointsToSet.iterator().asScala.foreach(prototypeIkey => {
              prototypeIkey.getCreationSites(callGraph).asScala.foreach(prototypeCreationSite => {
                val prototypeCGNode = prototypeCreationSite.fst
                // function and prototype has generated in the same CGNode
                if (functionCGNode.contains(prototypeCGNode)) {
                  addFunctionProtoObjCreationSite(objKey, prototypeIkey)
                }
              })
            })
            ////////////
            // TODO: Ignore ikey for prototype object in else branch
            ////////////

          } else {
            // for-in function
            forInFunctionSet += ikey
          }

        } ////////////
        // TODO: Ignore ikey for prototype object in else branch
        ////////////ax
        // for arguments, function_prototypes, and objects
        else {
          // lookup generated source position
          // if creation site is SSANewInstruction, created object is arguments type
          val creationSites = ikey.getCreationSites(callGraph).asScala
          creationSites.foreach(creationSite => {
            val (function, newsiteRefer) = (creationSite.fst, creationSite.snd)

            if (!function.getMethod().isSynthetic()) {
              val astMethod = function.getMethod().asInstanceOf[AstMethod]
              val functionName = astMethod.lexicalInfo().getScopingName()
              val startOffset = eventFunctionWALAStartOffSet(functionName)
              val index = newsiteRefer.getProgramCounter()
              val creationPosition = getPosition(astMethod.getSourcePosition(index))
              //              val function_name = ast_method.lexicalInfo().getScopingName()
              if (creationPosition != null) {
                // created object is arguments
                // arguments object should be lookedup from caller
                if (isArgumentsInstr(function, index) && (!callGraph.getSuccNodes(callGraph.getFakeRootNode()).asScala.contains(function))) {
                  // add lexical name to arguments object
                  // a function name-lexical name- has an arguments object as ikey
                  lexicalNameToArguments.get(functionName) match {
                    case Some(argSet) => lexicalNameToArguments += functionName -> (argSet + ikey)
                    case None => lexicalNameToArguments += functionName -> Set(ikey)
                  }
                  //////

                  val (argumentsPositionPairSet, argumentsStringSet) = getCallerPositionSet(function)
                  argumentsStringSet.foreach(arguments => {
                    argumentsCreationLexicalNameMap.get(arguments) match {
                      case Some(argumentsObjects) => argumentsCreationLexicalNameMap += arguments -> (argumentsObjects + ikey)
                      case None => argumentsCreationLexicalNameMap += arguments -> Set(ikey)
                    }
                  })
                  argumentsPositionPairSet.foreach(argumentsPositionPair => {
                    val (callerFunctionName, argumentsPosition) = argumentsPositionPair
                    val argPosition = getPosition(argumentsPosition)
                    val callerStartOffset = eventFunctionWALAStartOffSet(callerFunctionName)
                    val objKey = (argPosition.getURL().getFile(), (argPosition.getFirstOffset() + callerStartOffset, argPosition.getLastOffset() + callerStartOffset))
                    addArgumentsObjCreationSite(objKey, ikey)
                  })
                } // other objects
                else {
                  val objKey = (creationPosition.getURL().getFile(), (creationPosition.getFirstOffset() + startOffset, creationPosition.getLastOffset() + startOffset))
                  addObjCreationSite(objKey, ikey)
                }
              } // if creation_position is null in ast method, created object is arguments generated by for-in.
              else {
                forInArgumentsSet += ikey
              }

            } // objects that are created in synthetic function.
            // lookup caller positions
            else {
              //
              val functionName = function.getMethod().getReference().getDeclaringClass().getName().toString()

              // for arguments object in builtin function

              val newInstIndex = function.getIR().getNewInstructionIndex(newsiteRefer)
              val inst = function.getIR().getNew(newsiteRefer)
              val index = function.getIR().getNewInstructionIndex(newsiteRefer)

              val (callerPosPairSet, argumentsStringSet) = getCallerPositionSet(function)
              if (isArgumentsInstr(function, index) && (!callGraph.getSuccNodes(callGraph.getFakeRootNode()).asScala.contains(function)) &&
                (functionName.startsWith("LString") || functionName.startsWith("LFunction") || functionName.startsWith("LArray") || functionName.startsWith("LNumber") || functionName.startsWith("LObject") || functionName.startsWith("LRegExp") ||
                  functionName.startsWith("LStringObject") || functionName.startsWith("LFunctionObject") || functionName.startsWith("LArrayObject") || functionName.startsWith("LNumberObject") || functionName.startsWith("LObject") || functionName.startsWith("LRegExpObject") ||
                  functionName.equals("Lprologue.js/Function_prototype_apply") || functionName.equals("Lprologue.js/Function_prototype_call"))) {
                lexicalNameToArguments.get(functionName) match {
                  case Some(argSet) => lexicalNameToArguments += functionName -> (argSet + ikey)
                  case None => lexicalNameToArguments += functionName -> Set(ikey)
                }
                argumentsStringSet.foreach(arguments => {
                  argumentsCreationLexicalNameMap.get(arguments) match {
                    case Some(argumentsObjects) => argumentsCreationLexicalNameMap += arguments -> (argumentsObjects + ikey)
                    case None => argumentsCreationLexicalNameMap += arguments -> Set(ikey)
                  }
                })
                callerPosPairSet.foreach(argumentsPositionPair => {
                  val (callerFunctionName, argumentsPosition) = argumentsPositionPair
                  val callerStartOffset = eventFunctionWALAStartOffSet(callerFunctionName)
                  val objKey = (argumentsPosition.getURL().getFile(), (argumentsPosition.getFirstOffset() + callerStartOffset, argumentsPosition.getLastOffset() + callerStartOffset))
                  //addArgumentsObjCreationSite(objKey, ikey)
                })
                argumentsStringSet.foreach(arguments => {
                  argumentsCreationLexicalNameMap.get(arguments) match {
                    case Some(argumentsObjects) => argumentsCreationLexicalNameMap += arguments -> (argumentsObjects + ikey)
                    case None => argumentsCreationLexicalNameMap += arguments -> Set(ikey)
                  }
                })

              } else {
                callerPosPairSet.foreach(creationPositionPair => {
                  val (callerFunctionName, creationPosition) = creationPositionPair
                  val callerStartOffset = eventFunctionWALAStartOffSet(callerFunctionName)
                  val objKey = (creationPosition.getURL().getFile(), (creationPosition.getFirstOffset() + callerStartOffset, creationPosition.getLastOffset() + callerStartOffset))
                  addObjCreationSite(objKey, ikey)
                })
              }
            }
          })
        }
      }
      ///////////////////////
      // Generate WALA Heap
      ///////////////////////
      // To collect fields, lookup superClass.
      // if ikey is a function, there is no fields in getConcreteType; so we use getSuperClass to get all field names
      val objMap = getObjectMap(ikey)
      walaHeap += ikey -> objMap

    })
  }
  def getObjectMap(ikey: InstanceKey): Map[String, OrdinalSet[InstanceKey]] = {
    var fields = ikey.getConcreteType().getAllFields()
    if (fields.isEmpty()) {
      fields = ikey.getConcreteType().getSuperclass().getAllFields()
    }
    val fieldsScala = fields.asScala

    var objMap = Map[String, OrdinalSet[InstanceKey]]()
    fieldsScala.foreach(field => {
      val (fieldStr, pointsTo) = computesFieldPointsTo(ikey, field)
      if (fieldStr != null && pointsTo.size() > 0) {
        if (pointsTo.size == 1 && !pointsTo.toString.contains("ConstantKey:null") || pointsTo.size > 1)
          objMap += fieldStr -> pointsTo
      }
    })
    objMap
  }
  var walaHeap = Map[InstanceKey, Map[String, OrdinalSet[InstanceKey]]]()

  // NOTE: Do we need maintain creation node map?
  var creationNodeMap: Map[Loc, Node] = Map()
  // creation sites in source code level
  var safeCreationSiteLexical = Map[SourceLoc, Set[Loc]]()
  var safeCreationSiteFunction = Map[SourceLoc, Set[Loc]]()
  var safeCreationSiteFunctionPrototype = Map[SourceLoc, Set[Loc]]()
  var safeCreationSiteArguments = Map[SourceLoc, Set[Loc]]()
  var safeCreationSiteObject = Map[SourceLoc, Set[Loc]]()

  //  var safeSourceLocToFunctionIdMap = Map[SourceLoc, FunctionId]()
  var safeSourceLocToFunctionIdSetMap = Map[SourceLoc, Set[FunctionId]]()

  var callInstrs: Set[CFGInst] = Set()

  def traverseSAFECFG(): Unit = {

    val nodes = safeReachableFIdSet.foldLeft(List[Node]())((set, fid) => {
      // computes a map from source loc to fid
      val fidInfo = cfgUtil.getFuncInfo(fid)
      val sourceLocKey = (fidInfo.fileName, (fidInfo.begin.offset, fidInfo.end.offset + 1))
      //      safeSourceLocToFunctionIdMap += source_loc_key -> fid
      safeSourceLocToFunctionIdSetMap.get(sourceLocKey) match {
        case Some(fidset) => safeSourceLocToFunctionIdSetMap += sourceLocKey -> (fidset + fid)
        case None => safeSourceLocToFunctionIdSetMap += sourceLocKey -> Set(fid)
      }
      ///////////////////////
      set ++ cfgUtil.getReachableNodes(fid)
    })
    nodes.foreach(node => {
      cfgUtil.getBlock(node) match {
        case Entry(func) =>
        case Exit(func) =>
        case ExitExc(func) =>
        case _ =>
          val (fid, block) = node
          val startOffset = this.eventFunctionSAFEStartOffSet(fid)
          block.getInsts.foreach(instr => {
            instr match {
              case CFGAlloc(ir, b, lhs: CFGId, proto, asite) =>
                val loc = Loc(asite)
                creationNodeMap += loc -> node
                val info = instr.span
                safeCreationSiteObject = addCreationSite(fid, info, asite, safeCreationSiteObject)

              case CFGAllocArray(ir, b, lhs: CFGId, length, asite) =>
                val loc = Loc(asite)
                creationNodeMap += loc -> node
                val info = instr.span
                safeCreationSiteObject = addCreationSite(fid, info, asite, safeCreationSiteObject)

              case CFGAllocArg(ir, b, lhs: CFGId, length, asite) =>
                val loc = Loc(asite)
                creationNodeMap += loc -> node
                val info = instr.span
                safeCreationSiteArguments = addCreationSite(fid, info, asite, safeCreationSiteArguments)

              case CFGFunExpr(ir, b, lhs, name, func, asite1, asite2, asite3Opt) =>
                val loc1 = Loc(asite1)
                val loc2 = Loc(asite2)

                creationNodeMap += loc1 -> node
                creationNodeMap += loc2 -> node

                val info = instr.span
                safeCreationSiteFunction = addCreationSite(fid, info, asite1, safeCreationSiteFunction)
                safeCreationSiteFunctionPrototype = addCreationSite(fid, info, asite2, safeCreationSiteFunctionPrototype)

                asite3Opt match {
                  case Some(asite3) =>
                    val loc3 = Loc(asite3)
                    creationNodeMap += loc3 -> node
                    safeCreationSiteLexical = addCreationSite(fid, info, asite3, safeCreationSiteLexical)
                  case None =>
                }

              case CFGConstruct(ir, b, func, thisArg, arguments, asite) =>
                val loc = Loc(asite)
                creationNodeMap += loc -> node
                val info = instr.span
                safeCreationSiteLexical = addCreationSite(fid, info, asite, safeCreationSiteLexical)
                callInstrs += instr
              case CFGCall(ir, b, func, thisArg, arguments, asite) =>
                val loc = Loc(asite)
                creationNodeMap += loc -> node
                val info = instr.span
                safeCreationSiteLexical = addCreationSite(fid, info, asite, safeCreationSiteLexical)
                callInstrs += instr
              case CFGInternalCall(ir, b, lhs, name, arguments, asiteOpt) =>
                asiteOpt match {
                  case Some(asite) =>
                  // NOTE: WALA does not support implicit type conversion
                  case None => ()
                }
              case _ => ()
            }
          })
      }
    })
  }

  def eventFunctionWALAStartOffSet(funName: String): Int = {
    return 0
  }
  var eventFunctionSourceLocMap = Map[Position, Int]()
  def eventFunctionWALAStartOffSet(functionPosition: Position): Int = {
    if (functionPosition.isInstanceOf[IncludedPosition]) {
      val includedPosition = functionPosition.asInstanceOf[IncludedPosition].getIncludePosition
      eventFunctionSourceLocMap.get(includedPosition) match {
        case Some(offset) => offset
        case None => 0
      }
    } else 0
  }

  def eventFunctionSAFEStartOffSet(fid: FunctionId): Int = {
    /*if(eventFunctionIdSAFE.contains(fid)) {
 val fun_name_length = cfg.getFuncName(fid).length
 // function fun_name (event) {
 // basic length = 18,
 val start_offset = 19 + fun_name_length
 -start_offset
}*/
    0
  }

  def computesWALAObjToSAFELoc(): Unit = {
    // Function object
    addWALAtoSAFEStartOffset(safeCreationSiteFunction, functionCreationLocMap)
    // FunctionPrototype object
    addWALAtoSAFEStartOffset(safeCreationSiteFunctionPrototype, functionPrototypeCreationLocMap)

    // arguments Array
    addWALAtoSAFE(safeCreationSiteArguments, argumentsCreationLocMap)

    addWALAtoSAFE(safeCreationSiteObject, objCreationLocMap)
    // Lexical object

  }

  var walaToSafe: Map[InstanceKey, Set[Loc]] = Map()

  def addWALAtoSAFEStartOffset(safeCreationSite: Map[SourceLoc, Set[Loc]], WALAcreationSite: Map[SourceLoc, Set[InstanceKey]]): Unit = {
    // change keyset...
    var safeCreationSiteStartOffset = Map[(String, Int), Set[Loc]]()
    var WALAcreationSiteStartOffset = Map[(String, Int), Set[InstanceKey]]()
    safeCreationSite.foreach(kv => {
      val (sourceLoc, locSet) = kv
      val (lexical, offset) = sourceLoc
      val (firstOffset, lastOffset) = offset
      val newSourceLoc = (lexical, firstOffset)
      safeCreationSiteStartOffset.get(newSourceLoc) match {
        case Some(prevLocSet) => safeCreationSiteStartOffset += newSourceLoc -> (prevLocSet ++ locSet)
        case None => safeCreationSiteStartOffset += newSourceLoc -> locSet
      }
    })
    WALAcreationSite.foreach(kv => {
      val (sourceLoc, objSet) = kv
      val (lexical, offset) = sourceLoc
      val (firstOffset, lastOffset) = offset
      val newSourceLoc = (lexical, firstOffset)
      WALAcreationSiteStartOffset.get(newSourceLoc) match {
        case Some(prevObjSet) => WALAcreationSiteStartOffset += newSourceLoc -> (prevObjSet ++ objSet)
        case None => WALAcreationSiteStartOffset += newSourceLoc -> objSet
      }
    })

    safeCreationSiteStartOffset.foreach(SAFEPair => {
      val (position, locSet) = SAFEPair
      val walaObjects = WALAcreationSiteStartOffset.get(position) match {
        case Some(objects) => objects
        case None => Set[InstanceKey]()
      }
      walaObjects.foreach(obj => {
        walaToSafe.get(obj) match {
          case Some(prevLocSet) => walaToSafe += obj -> (prevLocSet ++ locSet)
          case None => walaToSafe += obj -> locSet
        }
      })
    })
  }

  def addWALAtoSAFE(safeCreationSite: Map[SourceLoc, Set[Loc]], walaCreationSite: Map[SourceLoc, Set[InstanceKey]]): Unit = {
    safeCreationSite.foreach(SAFEPair => {
      val (position, locSet) = SAFEPair
      val walaObjects = walaCreationSite.get(position) match {
        case Some(objects) => objects
        case None => Set[InstanceKey]()
      }
      walaObjects.foreach(obj => {
        walaToSafe.get(obj) match {
          case Some(prevLocSet) => walaToSafe += obj -> (prevLocSet ++ locSet)
          case None => walaToSafe += obj -> locSet
        }
      })
    })
  }

  def addWALAtoSAFE(walaObject: InstanceKey, safeLocset: Set[Loc]): Unit = {
    walaToSafe.get(walaObject) match {
      case Some(addrset) => walaToSafe += walaObject -> (addrset ++ safeLocset)
      case None => walaToSafe += walaObject -> safeLocset
    }
  }
  // Add builtin function name in WALA
  var builtinFunctionNameSet = Set[String]("LObject", "LArray", "LFunction", "LNumber", "LString", "LRegExp",
    "LObjectObject", "LArrayObject", "LFunctionObject", "LNumberObject", "LStringObject", "LRegExpObject")

  var walaBuiltinStrToSAFEBuiltinStrMap = Map[String, String]()
  val builtinStringSet = Set("Date", "RegExp", "Error")

  def sourceLocKeyToFunctionIdSetStartOffset(map: Map[SourceLoc, Set[FunctionId]]): Map[(String, Int), Set[FunctionId]] = {
    map.foldLeft(Map[(String, Int), Set[FunctionId]]())((map, kv) => {
      val (sourceLoc, fid) = kv
      val (lexicalName, offSet) = sourceLoc
      val (firstOffset, lastOffset) = offSet
      map + ((lexicalName, firstOffset) -> fid)
    })
  }

  var safeReachableFIdSet = Set[FunctionId]()
  var reachableBuiltinFunctionNameSet = Set[String]()
  var reachableFunctionSourceLocSet = Set[SourceLoc]()

  var safeToWalaIdMap: Map[Int, Int] = HashMap()

  def computesReachableFunctions(): Unit = {
    // Computes Function Info in SAFE
    // for functions in source code
    cfgUtil.getFunctionIds.filter(cfgUtil.isUserFunction).foreach(fid => {
      val info = cfgUtil.getFuncInfo(fid)
      safeToWalaIdMap += fid -> info.begin.offset
      val position = (info.fileName, (info.begin.offset, info.end.offset + 1))
      safeSourceLocToFunctionIdSetMap.get(position) match {
        case Some(fidset) => safeSourceLocToFunctionIdSetMap += position -> (fidset + fid)
        case None => safeSourceLocToFunctionIdSetMap += position -> Set(fid)
      }
    })

    // Computes reachable function set in SAFE
    safeReachableFIdSet += cfgUtil.getGlobalFId
    val safeSourceLocToFunctionIdSetMapStartOffset = sourceLocKeyToFunctionIdSetStartOffset(safeSourceLocToFunctionIdSetMap)
    reachableFunctionSourceLocSet.foreach(position => {
      val (lexicalName, sourceLoc) = position
      val (firstOffset, lastOffset) = sourceLoc
      val positionStartOffset = (lexicalName, firstOffset)
      // ignore builtin function in WALA
      if (!builtinFiles.contains(lexicalName)) {
        safeSourceLocToFunctionIdSetMapStartOffset.get(positionStartOffset) match {
          case Some(fidset) =>
            safeReachableFIdSet ++= fidset
          case None =>
            if (!quiet)
              println(" ** safeReachableFIdSet += " + position)
        }
      } else {
        if (!quiet)
          println(" ** safeReachableFIdSet += " + position)
      }
    })
  }

  var userGlobalObjcetMap = Map[String, OrdinalSet[InstanceKey]]()

  /* Computes Global Variables */
  def computesUserGlobalVariables(): Unit = {

    // Global variable from Global lexical variable
    // local variable map
    globalSourceLocMap.foreach(keyPair => {
      val (sourceLoc, lexicalName) = keyPair
      localVariableMap.get(sourceLoc) match {
        case Some(variableMap) =>
          val index = lexicalName.indexOf("/")
          if (index > 0) {
            val funcName = lexicalName.substring(index + 1)
            variableMap.get(funcName) match {
              case Some(pointsTo) => userGlobalObjcetMap += funcName -> pointsTo
              case None =>
            }
          } else {
            variableMap.foreach(kv => {
              val (variable, pointsTo) = kv
              if (!variable.equals("this") && !variable.equals("arguments") && !variable.contains("$$destructure")) {
                userGlobalObjcetMap += variable -> pointsTo
              }
            })
          }
        case None =>
      }
    })
  }

  var averagePointsTo = 0.0

  def walaPointsToAverage(): Unit = {
    var strCount = 0.0
    var ptsCount = 0.0
    var zeroPtsCount = 0.0
    // point-to set of Function, Function prototype, arguments and other Objects
    walaToSafe.keySet.foreach(ikey => {
      val obj = getObjectMap(ikey)
      obj.keySet.foreach(field => {
        if (!field.equals("length") && !field.equals("__proto__")) {
          obj.get(field) match {
            case Some(pointsTo) =>
              if (pointsTo.size() > 0) {
                strCount += 1
                ptsCount += pointsTo.size()
              } else zeroPtsCount += 1
            case None =>
          }
        }

      })
    })

    // points-to set of callee variable (not included in WALA argument object)
    safeCreationSiteArguments.foreach(pair => {
      val (pos, _) = pair
      callFunctionVariableMap.get(pos) match {
        case Some(pointsTo) =>
          if (pointsTo.size() > 0) {
            strCount += 1
            ptsCount += pointsTo.size()
          } else zeroPtsCount += 1
        case None =>
      }

      //argument of call-apply functions
      builtinArgumentsObject.get(pos) match {
        case Some(objMap) => {
          objMap.keySet.foreach(field => {
            objMap.get(field) match {
              case Some(pointsTo) =>
                if (pointsTo.size() > 0) {
                  strCount += 1
                  ptsCount += pointsTo.size()
                } else zeroPtsCount += 1
              case None =>
            }
          })
        }
        case None =>
      }
    })

    // points to analysis of user defined global variables
    userGlobalObjcetMap.foreach(fieldPair => {
      val (field, pointsTo) = fieldPair
      if (pointsTo.size() > 0) {
        strCount += 1
        ptsCount += pointsTo.size()
      } else zeroPtsCount += 1
    })

    averagePointsTo = ptsCount / strCount

    println("* Number of pointer variables : " + strCount)
    println("* Number of zero pointer variables : " + zeroPtsCount)
    println("* WALA averegae points-to analysis : " + averagePointsTo)
    println("percentage of zero points-to analysis : " + zeroPtsCount / (zeroPtsCount + strCount))
  }
}