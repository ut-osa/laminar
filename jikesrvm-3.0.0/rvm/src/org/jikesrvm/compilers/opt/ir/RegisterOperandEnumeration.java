/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt.ir;

import java.util.Enumeration;

import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;

/**
 * Extend java.util.Enumeration to avoid downcasts from object.
 */
public interface RegisterOperandEnumeration extends Enumeration<RegisterOperand> {
  /** Same as nextElement but avoid the need to downcast from Object */
  RegisterOperand next();
}

