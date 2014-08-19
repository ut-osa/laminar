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
package org.jikesrvm.compilers.opt.hir2lir;

import java.lang.reflect.Constructor;
import java.util.HashSet;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.Simple;
import org.jikesrvm.compilers.opt.controlflow.BranchOptimizations;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.inlining.InlineSequence;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.BasicBlockEnumeration;
import org.jikesrvm.compilers.opt.ir.Call;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Move;
import org.jikesrvm.compilers.opt.ir.OperandEnumeration;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.operand.MethodOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.runtime.Entrypoints;
import org.jikesrvm.scheduler.DIFC;
import org.jikesrvm.util.HashSetRVM;

public final class RemoveRedundantDIFCBarriers extends CompilerPhase {

  /**
   * Constructor for this compiler phase
   */
  private static final Constructor<CompilerPhase> constructor =
      getCompilerPhaseConstructor(RemoveRedundantDIFCBarriers.class);

  /**
   * Get a constructor object for this compiler phase
   * @return compiler phase constructor
   */
  public Constructor<CompilerPhase> getClassConstructor() {
    return constructor;
  }

  public boolean shouldPerform(OptOptions options) {
    return true;
  }

  public String getName() {
    return "Remove Redundant DIFC Barriers";
  }

  public void reportAdditionalStats() {
    VM.sysWrite("  ");
    VM.sysWrite(container.counter1 / container.counter2 * 100, 2);
    VM.sysWrite("% Infrequent RS calls");
  }

  /**
   * Remove redundant barriers if possible.
   */
  public void perform(IR ir) {
    // DIFC: redundant barrier elimination
    HashSetRVM<Instruction> fullRedInsts = null;
    HashSetRVM<Instruction> fullRedReads = computeRedundantBarriers(ir, true);
    HashSetRVM<Instruction> fullRedWrites = computeRedundantBarriers(ir, false);
    fullRedInsts = fullRedReads;
    fullRedInsts.addAll(fullRedWrites);

    boolean didSomething = false;
    for (Instruction inst : fullRedInsts) {
      
      if (DIFC.verbosity >= 2) {
        NormalMethod method = inst.position.getMethod();
        int line = method.getLineNumberForBCIndex(inst.bcIndex);
        System.out.println("[redundant late] " + method.getDeclaringClass() + "." + method.getName()  + " : " + line);
        InlineSequence position = inst.position;
        while (position.caller != null) {
          NormalMethod caller = position.caller.getMethod();
          int callerLine = caller.getLineNumberForBCIndex(position.bcIndex);
          System.out.println("     " + caller.getDeclaringClass() + "." + caller.getName()  + " : " + callerLine);
          position = position.caller;
        }
        System.out.println("  " + inst);
      }
      
      // remove the instruction!
      inst.remove();
      
      didSomething = true;
    }
    
    // If we actually removed something, clean up the mess
    if (didSomething) {
      branchOpts.perform(ir, true);
      _os.perform(ir);
    }
  }

  private final Simple _os = new Simple(1, false, false, false);
  private final BranchOptimizations branchOpts = new BranchOptimizations(-1, true, true);

  // DIFC: redundant barrier elimination when the barriers are already inserted
  static final HashSetRVM<Instruction> computeRedundantBarriers(IR ir, boolean reads) {
    // first set all the scratch objects to empty sets
    for (BasicBlock bb = ir.lastBasicBlockInCodeOrder();
    bb != null;
    bb = bb.prevBasicBlockInCodeOrder()) {
      bb.scratchObject = new HashSet<Register>();
    }
    ir.cfg.exit().scratchObject = new HashSet<Register>();

    HashSetRVM<Instruction> fullRedInsts = new HashSetRVM<Instruction>(); 

    // do data-flow
    HashSet<Register> thisFullRedSet = new HashSet<Register>();
    boolean changed;
    do {
      changed = false;
      for (BasicBlock bb = ir.firstBasicBlockInCodeOrder();
      bb != null;
      bb = bb.nextBasicBlockInCodeOrder()) {
        // compute redundant variables for the bottom of the block
        // and merge with redundant variables
        thisFullRedSet.clear();
        boolean first = true;
        for (BasicBlockEnumeration e = bb.getIn(); e.hasMoreElements(); ) {
          BasicBlock predBB = e.next();
          HashSet<Register> predFullRedSet = (HashSet<Register>)predBB.scratchObject;
          if (first) {
            thisFullRedSet.addAll(predFullRedSet);
            first = false;
          } else {
            thisFullRedSet.retainAll(predFullRedSet); // intersection
          }
        }

        // propagate info from top to bottom of block
        for (Instruction i = bb.firstInstruction(); !i.isBbLast(); i = i.nextInstructionInCodeOrder()) {
          // first look at RHS (since we're going forward)
          Operand useOperand = null;
          boolean isUse = false;
          if (Call.conforms(i)) {
            MethodOperand methodOperand = Call.getMethod(i);
            if (methodOperand != null) {
              RVMMethod target = methodOperand.getTarget();
              boolean isAlloc = false;
              if (target == Entrypoints.difcAllocBarrierDynamicMethod ||
                  target == Entrypoints.difcAllocBarrierInsideSRMethod) {
                isAlloc = true;
              } else if (reads &&
                         (target == Entrypoints.difcReadBarrierDynamicMethod ||
                          target == Entrypoints.difcReadBarrierInsideSRMethod ||
                          target == Entrypoints.difcReadBarrierOutsideSRMethod)) {
                isUse = true;
              } else if (!reads &&
                         (target == Entrypoints.difcWriteBarrierDynamicMethod ||
                          target == Entrypoints.difcWriteBarrierInsideSRMethod ||
                          target == Entrypoints.difcWriteBarrierOutsideSRMethod)) {
                isUse = true;
              }
              
              if (isAlloc || isUse) {
                useOperand = Call.getParam(i, 0);
              }
            }
          }
          if (useOperand != null) {
            //if (VM.VerifyAssertions) { VM._assert(useOperand.isRegister() || useOperand.isConstant()); }
            if (useOperand.isRegister()) {
              Register useReg = useOperand.asRegister().register;
              if (thisFullRedSet.contains(useReg) && isUse) {
                fullRedInsts.add(i);
              }
              thisFullRedSet.add(useReg);
            } else if (useOperand.isConstant()) {
              fullRedInsts.add(i);
            } else {
              System.out.println("Weird operand: " + useOperand);
              VM._assert(false);
            }
          }
          // now look at LHS
          if (Move.conforms(i)) {
            Operand srcOperand = Move.getVal(i);
            if (srcOperand.isRegister()) {
              Register useReg = srcOperand.asRegister().register;
              Register defReg = Move.getResult(i).register;
              if (thisFullRedSet.contains(useReg)) {
                thisFullRedSet.add(defReg);
              }
            }
          } else {
            // look at other defs
            for (OperandEnumeration e = i.getDefs(); e.hasMoreElements(); ) {
              Operand defOperand = e.next();
              if (defOperand.isRegister()) {
                Register defReg = defOperand.asRegister().register;
                thisFullRedSet.remove(defReg);
              }
            }
          }
        }

        // compare what we've computed with what was already there
        HashSet<Register> oldFullRedSet = (HashSet<Register>)bb.scratchObject;
        if (!oldFullRedSet.equals(thisFullRedSet)) {
          if (VM.VerifyAssertions) { VM._assert(thisFullRedSet.containsAll(oldFullRedSet)); }
          oldFullRedSet.clear();
          oldFullRedSet.addAll(thisFullRedSet);
          changed = true;
        }
      }
    } while (changed);

    // clear the scratch objects
    for (BasicBlock bb = ir.lastBasicBlockInCodeOrder();
    bb != null;
    bb = bb.prevBasicBlockInCodeOrder()) {
      bb.scratchObject = null;
    }
    ir.cfg.exit().scratchObject = null;
    
    return fullRedInsts;
  }

  
}
