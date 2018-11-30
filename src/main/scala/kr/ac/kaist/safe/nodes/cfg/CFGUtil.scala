package kr.ac.kaist.safe.nodes.cfg

import kr.ac.kaist.safe.util.Span

import scala.collection.immutable.HashMap

class CFGUtil(
    cfg: CFG
) {

  type Node = (FunctionId, CFGBlock)

  // all nodes in this cfg.
  private var nodes: List[Node] = List()
  private var funcIds: Set[FunctionId] = Set()

  cfg.getAllBlocks.foreach(block => {
    val node: Node = (block.func.id, block)
    nodes = (node) :: nodes
  })
  cfg.getAllFuncs.foreach(func => {
    funcIds += func.id
  })
  def getNodes: List[Node] = nodes

  def getGlobalFId: FunctionId = cfg.globalFunc.id

  def getFunctionIds: Set[FunctionId] = funcIds
  def getFunc(fid: FunctionId): Option[CFGFunction] = cfg.getFunc(fid)

  def isUserFunction(fid: FunctionId): Boolean = {
    cfg.getFunc(fid) match {
      case Some(func) => func.isUser
      case None => false
    }
  }

  def getBlock(node: Node): CFGBlock = {
    val (_, block) = node
    block
  }

  def getFuncInfo(fid: FunctionId): Span = {
    val fidInfo = cfg.getFunc(fid) match {
      case Some(f) => f.span
    }
    fidInfo
  }

  private var reachableNodes = HashMap[FunctionId, List[Node]]()
  computeReachableNodes()
  def computeReachableNodes(): Unit = computeReachableNodes(quiet = false)

  def computeReachableNodes(quiet: Boolean): Unit = {
    val blocks = cfg.getAllBlocks
    blocks.foreach(block => {
      val fid = block.func.id
      reachableNodes.get(fid) match {
        case Some(nodes) =>
          reachableNodes += (fid -> ((fid, block) :: nodes))
        case None =>
          reachableNodes += (fid -> List[Node]((fid, block)))
      }
    })
    //val functions = cfg.getAllFuncs // get all functions
    // for each function, computes reachable nodes from the function entry node
    /*if (!quiet)
      System.out.println("# computes reachable nodes")
    functions.foreach(func => {
      reachableNodes += (func.id -> (reachable((func.id, func.entry))))
    })*/
  }

  private def reachable(e: Node): List[Node] = {
    var visited = Set[Node]()
    var result = List[Node]()

    def dfs(n: Node): Unit = {
      visited += (n)
      val (fid, block) = n
      if (fid >= 0) {
        var temp = block
      }
      block.getAllSucc.foreach(map => {
        val (_, blockList) = map
        blockList.foreach(b => {
          val newNode = (b.func.id, b)
          if (!visited.contains(newNode)) {
            dfs(newNode)
          }

        })
      })
      result = (n) :: result
    }
    dfs(e)

    visited
    result
  }

  def getReachableNodes(fid: FunctionId): List[Node] = {

    reachableNodes.get(fid) match {
      case Some(s) => s
      case None => {
        System.err.println("* Warning: there is no pre-computed reachable node for " + fid)
        getNodes.filter(n => {
          val (n1, n2) = n
          n1 == fid
        }) // just filter out by checking "node.fid == fid
      }
    }

  }

}
