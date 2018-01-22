/******************************************************************************
 * Copyright (c) 2002 - 2006 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *****************************************************************************/
package com.ibm.wala.cast.ir.ssa;

import com.ibm.wala.ssa.SSAInstruction;

public interface AstInstructionVisitor extends SSAInstruction.IVisitor {

  void visitAstLexicalRead(AstLexicalRead instruction);
    
  void visitAstLexicalWrite(AstLexicalWrite instruction);
    
  void visitAstGlobalRead(AstGlobalRead instruction);
    
  void visitAstGlobalWrite(AstGlobalWrite instruction);

  void visitAssert(AstAssertInstruction instruction);

  void visitEachElementGet(EachElementGetInstruction inst);

  void visitEachElementHasNext(EachElementHasNextInstruction inst);

  void visitIsDefined(AstIsDefinedInstruction inst);

  void visitEcho(AstEchoInstruction inst);
}

