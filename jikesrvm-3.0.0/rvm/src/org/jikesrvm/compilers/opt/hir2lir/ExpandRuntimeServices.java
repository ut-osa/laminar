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

import static org.jikesrvm.compilers.opt.driver.OptConstants.RUNTIME_SERVICES_BCI;
import static org.jikesrvm.compilers.opt.ir.Operators.ATHROW_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.CALL;
import static org.jikesrvm.compilers.opt.ir.Operators.GETFIELD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.GETSTATIC_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_ASTORE;
import static org.jikesrvm.compilers.opt.ir.Operators.MONITORENTER_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.MONITOREXIT_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.NEWARRAY_UNRESOLVED_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.NEWARRAY;
import static org.jikesrvm.compilers.opt.ir.Operators.NEWARRAY_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.NEWOBJMULTIARRAY_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.NEW_UNRESOLVED_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.NEW_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.PUTFIELD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.PUTSTATIC_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_ALOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_ASTORE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_MOVE;

// DIFC: need extra import statics
import static org.jikesrvm.compilers.opt.ir.Operators.INT_ALOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.LONG_ALOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.FLOAT_ALOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.DOUBLE_ALOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.UBYTE_ALOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.BYTE_ALOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.USHORT_ALOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.SHORT_ALOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_ASTORE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.LONG_ASTORE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.FLOAT_ASTORE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.DOUBLE_ASTORE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.BYTE_ASTORE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.SHORT_ASTORE_opcode;

import java.lang.reflect.Constructor;
import java.util.HashSet;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.classloader.RVMArray;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.FieldReference;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.Simple;
import org.jikesrvm.compilers.opt.controlflow.BranchOptimizations;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.inlining.InlineDecision;
import org.jikesrvm.compilers.opt.inlining.InlineSequence;
import org.jikesrvm.compilers.opt.inlining.Inliner;
import org.jikesrvm.compilers.opt.ir.ALoad;
import org.jikesrvm.compilers.opt.ir.AStore;
import org.jikesrvm.compilers.opt.ir.Athrow;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.BasicBlockEnumeration;
import org.jikesrvm.compilers.opt.ir.Call;
import org.jikesrvm.compilers.opt.ir.GetField;
import org.jikesrvm.compilers.opt.ir.GetStatic;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.IRTools;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.MonitorOp;
import org.jikesrvm.compilers.opt.ir.Multianewarray;
import org.jikesrvm.compilers.opt.ir.Move;
import org.jikesrvm.compilers.opt.ir.New;
import org.jikesrvm.compilers.opt.ir.NewArray;
import org.jikesrvm.compilers.opt.ir.OperandEnumeration;
import org.jikesrvm.compilers.opt.ir.PutField;
import org.jikesrvm.compilers.opt.ir.PutStatic;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.operand.IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.LocationOperand;
import org.jikesrvm.compilers.opt.ir.operand.MethodOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.ir.operand.TypeOperand;
import org.jikesrvm.mm.mminterface.MemoryManagerConstants;
import org.jikesrvm.mm.mminterface.MemoryManager;
import org.jikesrvm.objectmodel.ObjectModel;
import org.jikesrvm.runtime.Entrypoints;
import org.jikesrvm.scheduler.DIFC;
import org.jikesrvm.util.HashSetRVM;

/**
 * As part of the expansion of HIR into LIR, this compile phase
 * replaces all HIR operators that are implemented as calls to
 * VM service routines with CALLs to those routines.
 * For some (common and performance critical) operators, we
 * may optionally inline expand the call (depending on the
 * the values of the relevant compiler options and/or Controls).
 * This pass is also responsible for inserting write barriers
 * if we are using an allocator that requires them. Write barriers
 * are always inline expanded.
 */
public final class ExpandRuntimeServices extends CompilerPhase {

  /**
   * Constructor for this compiler phase
   */
  private static final Constructor<CompilerPhase> constructor =
      getCompilerPhaseConstructor(ExpandRuntimeServices.class);

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
    return "Expand Runtime Services";
  }

  public void reportAdditionalStats() {
    VM.sysWrite("  ");
    VM.sysWrite(container.counter1 / container.counter2 * 100, 2);
    VM.sysWrite("% Infrequent RS calls");
  }

  /**
   * Given an HIR, expand operators that are implemented as calls to
   * runtime service methods. This method should be called as one of the
   * first steps in lowering HIR into LIR.
   *
   * @param ir  The HIR to expand
   */
  public void perform(IR ir) {
    ir.gc.resync(); // resync generation context -- yuck...

    // DIFC: don't add a barrier for the same instruction twice, which seems to be
    // happening occasionally when inlining reorders the code (?)
    HashSetRVM<Instruction> instsProcessedByDIFC = new HashSetRVM<Instruction>();

    // DIFC: redundant barrier elimination
    HashSetRVM<Instruction> fullRedInsts = null;
    if (VM.difcBarriers) {
      HashSetRVM<Instruction> fullRedReads = computeRedundantReadBarriers(ir, true);
      HashSetRVM<Instruction> fullRedWrites = computeRedundantReadBarriers(ir, false);
      fullRedInsts = fullRedReads;
      fullRedInsts.addAll(fullRedWrites);
    }

    Instruction next;
    for (Instruction inst = ir.firstInstructionInCodeOrder(); inst != null; inst = next) {
      next = inst.nextInstructionInCodeOrder();
      int opcode = inst.getOpcode();

      switch (opcode) {

        case NEW_opcode: {
          TypeOperand Type = New.getClearType(inst);
          RVMClass cls = (RVMClass) Type.getVMType();
          IntConstantOperand hasFinalizer = IRTools.IC(cls.hasFinalizer() ? 1 : 0);
          RVMMethod callSite = inst.position.getMethod();
          IntConstantOperand allocator = IRTools.IC(MemoryManager.pickAllocator(cls, callSite));
          IntConstantOperand align = IRTools.IC(ObjectModel.getAlignment(cls));
          IntConstantOperand offset = IRTools.IC(ObjectModel.getOffsetForAlignment(cls, false));
          Operand tib = ConvertToLowLevelIR.getTIB(inst, ir, Type);
          if (VM.BuildForIA32 && VM.runningVM) {
            // shield BC2IR from address constants
            RegisterOperand tmp = ir.regpool.makeTemp(TypeReference.TIB);
            inst.insertBefore(Move.create(REF_MOVE, tmp, tib));
            tib = tmp.copyRO();
          }
          // DIFC: allocate labeled object if in secure region
          IntConstantOperand site = IRTools.IC(MemoryManager.getDIFCAllocationSite(true, inst.position.method));
          //IntConstantOperand site = IRTools.IC(MemoryManager.getAllocationSite(true));
          RVMMethod target = Entrypoints.resolvedNewScalarMethod;
          Call.mutate7(inst,
                       CALL,
                       New.getClearResult(inst),
                       IRTools.AC(target.getOffset()),
                       MethodOperand.STATIC(target),
                       IRTools.IC(cls.getInstanceSize()),
                       tib,
                       hasFinalizer,
                       allocator,
                       align,
                       offset,
                       site);
          next = inst.prevInstructionInCodeOrder();
          
          // DIFC: allocation barrier
          insertAllocBarrier(inst, ir, instsProcessedByDIFC, fullRedInsts);
          
          if (ir.options.INLINE_NEW) {
            if (inst.getBasicBlock().getInfrequent()) container.counter1++;
            container.counter2++;
            if (!ir.options.FREQ_FOCUS_EFFORT || !inst.getBasicBlock().getInfrequent()) {
              inline(inst, ir);
            }
          }
        }
        break;

        case NEW_UNRESOLVED_opcode: {
          int typeRefId = New.getType(inst).getTypeRef().getId();
          RVMMethod target = Entrypoints.unresolvedNewScalarMethod;
          // DIFC: allocate labeled object if in secure region
          IntConstantOperand site = IRTools.IC(MemoryManager.getDIFCAllocationSite(true, inst.position.method));
          //IntConstantOperand site = IRTools.IC(MemoryManager.getAllocationSite(true));
          Call.mutate2(inst,
                       CALL,
                       New.getClearResult(inst),
                       IRTools.AC(target.getOffset()),
                       MethodOperand.STATIC(target),
                       IRTools.IC(typeRefId),
                       site);
          
          // DIFC: allocation barrier
          boolean inserted = insertAllocBarrier(inst, ir, instsProcessedByDIFC, fullRedInsts);
          if (inserted) {
            next = inst;
          }
        }
        break;

        case NEWARRAY_opcode: {
          TypeOperand Array = NewArray.getClearType(inst);
          RVMArray array = (RVMArray) Array.getVMType();
          Operand numberElements = NewArray.getClearSize(inst);
          boolean inline = numberElements instanceof IntConstantOperand;
          Operand width = IRTools.IC(array.getLogElementSize());
          Operand headerSize = IRTools.IC(ObjectModel.computeArrayHeaderSize(array));
          RVMMethod callSite = inst.position.getMethod();
          IntConstantOperand allocator = IRTools.IC(MemoryManager.pickAllocator(array, callSite));
          IntConstantOperand align = IRTools.IC(ObjectModel.getAlignment(array));
          IntConstantOperand offset = IRTools.IC(ObjectModel.getOffsetForAlignment(array, false));
          Operand tib = ConvertToLowLevelIR.getTIB(inst, ir, Array);
          if (VM.BuildForIA32 && VM.runningVM) {
            // shield BC2IR from address constants
            RegisterOperand tmp = ir.regpool.makeTemp(TypeReference.TIB);
            inst.insertBefore(Move.create(REF_MOVE, tmp, tib));
            tib = tmp.copyRO();
          }
          // DIFC: allocate labeled object if in secure region
          IntConstantOperand site = IRTools.IC(MemoryManager.getDIFCAllocationSite(true, inst.position.method));
          //IntConstantOperand site = IRTools.IC(MemoryManager.getAllocationSite(true));
          RVMMethod target = Entrypoints.resolvedNewArrayMethod;
          Call.mutate8(inst,
                       CALL,
                       NewArray.getClearResult(inst),
                       IRTools.AC(target.getOffset()),
                       MethodOperand.STATIC(target),
                       numberElements,
                       width,
                       headerSize,
                       tib,
                       allocator,
                       align,
                       offset,
                       site);
          next = inst.prevInstructionInCodeOrder();
          
          // DIFC: allocation barrier
          insertAllocBarrier(inst, ir, instsProcessedByDIFC, fullRedInsts);

          if (inline && ir.options.INLINE_NEW) {
            if (inst.getBasicBlock().getInfrequent()) container.counter1++;
            container.counter2++;
            if (!ir.options.FREQ_FOCUS_EFFORT || !inst.getBasicBlock().getInfrequent()) {
              inline(inst, ir);
            }
          }
        }
        break;

        case NEWARRAY_UNRESOLVED_opcode: {
          int typeRefId = NewArray.getType(inst).getTypeRef().getId();
          Operand numberElements = NewArray.getClearSize(inst);
          RVMMethod target = Entrypoints.unresolvedNewArrayMethod;
          // DIFC: allocate labeled object if in secure region
          IntConstantOperand site = IRTools.IC(MemoryManager.getDIFCAllocationSite(true, inst.position.method));
          //IntConstantOperand site = IRTools.IC(MemoryManager.getAllocationSite(true));
          Call.mutate3(inst,
                       CALL,
                       NewArray.getClearResult(inst),
                       IRTools.AC(target.getOffset()),
                       MethodOperand.STATIC(target),
                       numberElements,
                       IRTools.IC(typeRefId),
                       site);
          
          // DIFC: allocation barrier
          boolean inserted = insertAllocBarrier(inst, ir, instsProcessedByDIFC, fullRedInsts);
          if (inserted) {
            next = inst;
          }
        }
        break;

        case NEWOBJMULTIARRAY_opcode: {
          int dimensions = Multianewarray.getNumberOfDimensions(inst);
          RVMMethod callSite = inst.position.getMethod();
          int typeRefId = Multianewarray.getType(inst).getTypeRef().getId();
          if (dimensions == 2) {
            RVMMethod target = Entrypoints.optNew2DArrayMethod;
            Call.mutate4(inst,
                         CALL,
                         Multianewarray.getClearResult(inst),
                         IRTools.AC(target.getOffset()),
                         MethodOperand.STATIC(target),
                         IRTools.IC(callSite.getId()),
                         Multianewarray.getClearDimension(inst, 0),
                         Multianewarray.getClearDimension(inst, 1),
                         IRTools.IC(typeRefId));
          } else {
            // Step 1: Create an int array to hold the dimensions.
            TypeOperand dimArrayType = new TypeOperand(RVMArray.IntArray);
            RegisterOperand dimArray = ir.regpool.makeTemp(TypeReference.IntArray);
            dimArray.setPreciseType();
            next =  NewArray.create(NEWARRAY, dimArray, dimArrayType, new IntConstantOperand(dimensions));
            inst.insertBefore(next);
            // Step 2: Assign the dimension values to dimArray
            for (int i = 0; i < dimensions; i++) {
              LocationOperand loc = new LocationOperand(TypeReference.Int);
              inst.insertBefore(AStore.create(INT_ASTORE,
                                Multianewarray.getClearDimension(inst, i),
                                dimArray.copyD2U(),
                                IRTools.IC(i),
                                loc,
                                IRTools.TG()));
            }
            // Step 3. Plant call to OptLinker.newArrayArray
            RVMMethod target = Entrypoints.optNewArrayArrayMethod;
            Call.mutate3(inst,
                         CALL,
                         Multianewarray.getClearResult(inst),
                         IRTools.AC(target.getOffset()),
                         MethodOperand.STATIC(target),
                         IRTools.IC(callSite.getId()),
                         dimArray.copyD2U(),
                         IRTools.IC(typeRefId));
          }
          
          // DIFC: allocation barrier
          boolean inserted = insertAllocBarrier(inst, ir, instsProcessedByDIFC, fullRedInsts);
          if (inserted && dimensions == 2) {
            next = inst;
          }
        }
        break;

        case ATHROW_opcode: {
          RVMMethod target = Entrypoints.athrowMethod;
          MethodOperand methodOp = MethodOperand.STATIC(target);
          methodOp.setIsNonReturningCall(true);   // Record the fact that this is a non-returning call.
          Call.mutate1(inst, CALL, null, IRTools.AC(target.getOffset()), methodOp, Athrow.getClearValue(inst));
        }
        break;

        case MONITORENTER_opcode: {
          if (ir.options.NO_SYNCHRO) {
            inst.remove();
          } else {
            Operand ref = MonitorOp.getClearRef(inst);
            RVMType refType = ref.getType().peekType();
            if (refType != null && !refType.getThinLockOffset().isMax()) {
              RVMMethod target = Entrypoints.inlineLockMethod;
              Call.mutate2(inst,
                           CALL,
                           null,
                           IRTools.AC(target.getOffset()),
                           MethodOperand.STATIC(target),
                           MonitorOp.getClearGuard(inst),
                           ref,
                           IRTools.AC(refType.getThinLockOffset()));
              if (inst.getBasicBlock().getInfrequent()) container.counter1++;
              container.counter2++;
              if (!ir.options.FREQ_FOCUS_EFFORT || !inst.getBasicBlock().getInfrequent()) {
                inline(inst, ir);
              }
            } else {
              RVMMethod target = Entrypoints.lockMethod;
              Call.mutate1(inst,
                           CALL,
                           null,
                           IRTools.AC(target.getOffset()),
                           MethodOperand.STATIC(target),
                           MonitorOp.getClearGuard(inst),
                           ref);
            }
          }
          break;
        }

        case MONITOREXIT_opcode: {
          if (ir.options.NO_SYNCHRO) {
            inst.remove();
          } else {
            Operand ref = MonitorOp.getClearRef(inst);
            RVMType refType = ref.getType().peekType();
            if (refType != null && !refType.getThinLockOffset().isMax()) {
              RVMMethod target = Entrypoints.inlineUnlockMethod;
              Call.mutate2(inst,
                           CALL,
                           null,
                           IRTools.AC(target.getOffset()),
                           MethodOperand.STATIC(target),
                           MonitorOp.getClearGuard(inst),
                           ref,
                           IRTools.AC(refType.getThinLockOffset()));
              if (inst.getBasicBlock().getInfrequent()) container.counter1++;
              container.counter2++;
              if (!ir.options.FREQ_FOCUS_EFFORT || !inst.getBasicBlock().getInfrequent()) {
                inline(inst, ir);
              }
            } else {
              RVMMethod target = Entrypoints.unlockMethod;
              Call.mutate1(inst,
                           CALL,
                           null,
                           IRTools.AC(target.getOffset()),
                           MethodOperand.STATIC(target),
                           MonitorOp.getClearGuard(inst),
                           ref);
            }
          }
        }
        break;

        // DIFC: array write barriers
        case INT_ASTORE_opcode:
        case LONG_ASTORE_opcode:
        case FLOAT_ASTORE_opcode:
        case DOUBLE_ASTORE_opcode:
        case REF_ASTORE_opcode:
        case BYTE_ASTORE_opcode:
        case SHORT_ASTORE_opcode: {
          NormalMethod barrierMethod = DIFC.addBarriers(inst, DIFC.WRITE_BARRIER);
          Instruction beforeDIFCBarrier = null;
          if (barrierMethod != null &&
              !instsProcessedByDIFC.contains(inst) &&
              !redundant(inst, fullRedInsts)) {
            instsProcessedByDIFC.add(inst);
            Instruction barrier =
              Call.create1(CALL,
                           null,
                           IRTools.AC(barrierMethod.getOffset()),
                           MethodOperand.STATIC(barrierMethod),
                           AStore.getArray(inst).copy()/*,
                           AStore.getIndex(inst).copy()*/);
            beforeDIFCBarrier = insertAndMaybeInline(barrier, inst, ir, true);
          }
          if (opcode == REF_ASTORE_opcode) {
            if (MemoryManagerConstants.NEEDS_WRITE_BARRIER) {
              RVMMethod target = Entrypoints.arrayStoreWriteBarrierMethod;
              Instruction wb =
                  Call.create3(CALL,
                               null,
                               IRTools.AC(target.getOffset()),
                               MethodOperand.STATIC(target),
                               AStore.getClearGuard(inst),
                               AStore.getArray(inst).copy(),
                               AStore.getIndex(inst).copy(),
                               AStore.getValue(inst).copy());
              wb.bcIndex = RUNTIME_SERVICES_BCI;
              wb.position = inst.position;
              inst.replace(wb);
              next = wb.prevInstructionInCodeOrder();
              if (ir.options.INLINE_WRITE_BARRIER) {
                inline(wb, ir, true);
              }
            }
          }
          if (beforeDIFCBarrier != null) {
            next = beforeDIFCBarrier;
          }
        }
        break;

        // DIFC: array read barriers
        case INT_ALOAD_opcode:
        case LONG_ALOAD_opcode:
        case FLOAT_ALOAD_opcode:
        case DOUBLE_ALOAD_opcode:
        case REF_ALOAD_opcode:
        case UBYTE_ALOAD_opcode:
        case BYTE_ALOAD_opcode:
        case USHORT_ALOAD_opcode:
        case SHORT_ALOAD_opcode: {
          NormalMethod barrierMethod = DIFC.addBarriers(inst, DIFC.READ_BARRIER);
          Instruction beforeDIFCBarrier = null;
          if (barrierMethod != null &&
              !instsProcessedByDIFC.contains(inst) &&
              !redundant(inst, fullRedInsts)) {
            instsProcessedByDIFC.add(inst);
            Instruction barrier =
              Call.create1(CALL,
                           null,
                           IRTools.AC(barrierMethod.getOffset()),
                           MethodOperand.STATIC(barrierMethod),
                           ALoad.getArray(inst).copy());
            beforeDIFCBarrier = insertAndMaybeInline(barrier, inst, ir, true);
          }
          if (opcode == REF_ALOAD_opcode) {
            if (MemoryManagerConstants.NEEDS_READ_BARRIER) {
              RVMMethod target = Entrypoints.arrayLoadReadBarrierMethod;
              Instruction rb =
                Call.create2(CALL,
                    ALoad.getClearResult(inst),
                    IRTools.AC(target.getOffset()),
                    MethodOperand.STATIC(target),
                    ALoad.getClearGuard(inst),
                    ALoad.getArray(inst).copy(),
                    ALoad.getIndex(inst).copy());
              rb.bcIndex = RUNTIME_SERVICES_BCI;
              rb.position = inst.position;
              inst.replace(rb);
              next = rb.prevInstructionInCodeOrder();
              inline(rb, ir, true);
            }
          }
          if (beforeDIFCBarrier != null) {
            next = beforeDIFCBarrier;
          }
        }
        break;

        case PUTFIELD_opcode: {
          // DIFC: field write barriers
          NormalMethod barrierMethod = DIFC.addBarriers(inst, DIFC.WRITE_BARRIER);
          Instruction beforeDIFCBarrier = null;
          if (barrierMethod != null &&
              !instsProcessedByDIFC.contains(inst) &&
              !redundant(inst, fullRedInsts)) {
            instsProcessedByDIFC.add(inst);
            Instruction barrier =
              Call.create1(CALL,
                           null,
                           IRTools.AC(barrierMethod.getOffset()),
                           MethodOperand.STATIC(barrierMethod),
                           PutField.getRef(inst).copy());
            beforeDIFCBarrier = insertAndMaybeInline(barrier, inst, ir, true);
          }
          if (MemoryManagerConstants.NEEDS_WRITE_BARRIER) {
            LocationOperand loc = PutField.getLocation(inst);
            FieldReference fieldRef = loc.getFieldRef();
            if (!fieldRef.getFieldContentsType().isPrimitiveType()) {
              RVMField field = fieldRef.peekResolvedField();
              if (field == null || !field.isUntraced()) {
                RVMMethod target = Entrypoints.putfieldWriteBarrierMethod;
                Instruction wb =
                    Call.create4(CALL,
                                 null,
                                 IRTools.AC(target.getOffset()),
                                 MethodOperand.STATIC(target),
                                 PutField.getClearGuard(inst),
                                 PutField.getRef(inst).copy(),
                                 PutField.getOffset(inst).copy(),
                                 PutField.getValue(inst).copy(),
                                 IRTools.IC(fieldRef.getId()));
                wb.bcIndex = RUNTIME_SERVICES_BCI;
                wb.position = inst.position;
                inst.replace(wb);
                next = wb.prevInstructionInCodeOrder();
                if (ir.options.INLINE_WRITE_BARRIER) {
                  inline(wb, ir, true);
                }
              }
            }
          }
          if (beforeDIFCBarrier != null) {
            next = beforeDIFCBarrier;
          }
        }
        break;

        case GETFIELD_opcode: {
          // DIFC: field read barriers
          NormalMethod barrierMethod = DIFC.addBarriers(inst, DIFC.READ_BARRIER);
          Instruction beforeDIFCBarrier = null;
          if (barrierMethod != null &&
              !instsProcessedByDIFC.contains(inst) &&
              !redundant(inst, fullRedInsts)) {
            instsProcessedByDIFC.add(inst);
            Instruction barrier =
              Call.create1(CALL,
                           null,
                           IRTools.AC(barrierMethod.getOffset()),
                           MethodOperand.STATIC(barrierMethod),
                           GetField.getRef(inst).copy());
            beforeDIFCBarrier = insertAndMaybeInline(barrier, inst, ir, true);
          }
          if (MemoryManagerConstants.NEEDS_READ_BARRIER) {
            LocationOperand loc = GetField.getLocation(inst);
            FieldReference fieldRef = loc.getFieldRef();
            if (GetField.getResult(inst).getType().isReferenceType()) {
              RVMField field = fieldRef.peekResolvedField();
              if (field == null || !field.isUntraced()) {
                RVMMethod target = Entrypoints.getfieldReadBarrierMethod;
                Instruction rb =
                  Call.create3(CALL,
                               GetField.getClearResult(inst),
                               IRTools.AC(target.getOffset()),
                               MethodOperand.STATIC(target),
                               GetField.getClearGuard(inst),
                               GetField.getRef(inst).copy(),
                               GetField.getOffset(inst).copy(),
                               IRTools.IC(fieldRef.getId()));
                rb.bcIndex = RUNTIME_SERVICES_BCI;
                rb.position = inst.position;
                inst.replace(rb);
                next = rb.prevInstructionInCodeOrder();
                inline(rb, ir, true);
              }
            }
          }
          if (beforeDIFCBarrier != null) {
            next = beforeDIFCBarrier;
          }
        }
        break;

        case PUTSTATIC_opcode: {
          // DIFC: static write barrier
          NormalMethod barrierMethod = DIFC.addBarriers(inst, DIFC.STATIC_WRITE_BARRIER);
          Instruction beforeDIFCBarrier = null;
          if (barrierMethod != null &&
              !instsProcessedByDIFC.contains(inst) &&
              !redundant(inst, fullRedInsts)) {
            instsProcessedByDIFC.add(inst);
            Instruction barrier =
              Call.create1(CALL,
                           null,
                           IRTools.AC(barrierMethod.getOffset()),
                           MethodOperand.STATIC(barrierMethod),
                           IRTools.IC(PutStatic.getLocation(inst).getFieldRef().getId()));
            beforeDIFCBarrier = insertAndMaybeInline(barrier, inst, ir, true);
          }
          if (MemoryManagerConstants.NEEDS_PUTSTATIC_WRITE_BARRIER) {
            LocationOperand loc = PutStatic.getLocation(inst);
            FieldReference field = loc.getFieldRef();
            if (!field.getFieldContentsType().isPrimitiveType()) {
              RVMMethod target = Entrypoints.putstaticWriteBarrierMethod;
              Instruction wb =
                  Call.create3(CALL,
                               null,
                               IRTools.AC(target.getOffset()),
                               MethodOperand.STATIC(target),
                               PutStatic.getOffset(inst).copy(),
                               PutStatic.getValue(inst).copy(),
                               IRTools.IC(field.getId()));
              wb.bcIndex = RUNTIME_SERVICES_BCI;
              wb.position = inst.position;
              inst.replace(wb);
              next = wb.prevInstructionInCodeOrder();
              if (ir.options.INLINE_WRITE_BARRIER) {
                inline(wb, ir, true);
              }
            }
          }
          if (beforeDIFCBarrier != null) {
            next = beforeDIFCBarrier;
          }
        }
        break;

        case GETSTATIC_opcode: {
          // DIFC: static read barrier
          NormalMethod barrierMethod = DIFC.addBarriers(inst, DIFC.STATIC_READ_BARRIER);
          Instruction beforeDIFCBarrier = null;
          if (barrierMethod != null &&
              !instsProcessedByDIFC.contains(inst) &&
              !redundant(inst, fullRedInsts)) {
            instsProcessedByDIFC.add(inst);
            Instruction barrier =
              Call.create1(CALL,
                           null,
                           IRTools.AC(barrierMethod.getOffset()),
                           MethodOperand.STATIC(barrierMethod),
                           IRTools.IC(GetStatic.getLocation(inst).getFieldRef().getId()));
            beforeDIFCBarrier = insertAndMaybeInline(barrier, inst, ir, true);
          }
          if (MemoryManagerConstants.NEEDS_GETSTATIC_READ_BARRIER) {
            LocationOperand loc = GetStatic.getLocation(inst);
            FieldReference field = loc.getFieldRef();
            if (!field.getFieldContentsType().isPrimitiveType()) {
              RVMMethod target = Entrypoints.getstaticReadBarrierMethod;
              Instruction rb =
                  Call.create2(CALL,
                               GetStatic.getClearResult(inst),
                               IRTools.AC(target.getOffset()),
                               MethodOperand.STATIC(target),
                               GetStatic.getOffset(inst).copy(),
                               IRTools.IC(field.getId()));
              rb.bcIndex = RUNTIME_SERVICES_BCI;
              rb.position = inst.position;
              inst.replace(rb);
              next = rb.prevInstructionInCodeOrder();
              inline(rb, ir, true);
            }
          }
          if (beforeDIFCBarrier != null) {
            next = beforeDIFCBarrier;
          }
        }
        break;

        default:
          break;
      }
    }

    // If we actually inlined anything, clean up the mess
    if (didSomething) {
      branchOpts.perform(ir, true);
      _os.perform(ir);
    }
    // signal that we do not intend to use the gc in other phases anymore.
    ir.gc.close();
  }

  /**
   * DIFC: helper method for inserting allocation barriers
   */
  private boolean insertAllocBarrier(Instruction inst, IR ir, HashSetRVM<Instruction> instsProcessedByDIFC, HashSetRVM<Instruction> fullRedInsts) {
    NormalMethod barrierMethod = DIFC.addBarriers(inst, DIFC.ALLOC_BARRIER);
    if (barrierMethod != null &&
        !instsProcessedByDIFC.contains(inst) &&
        !redundant(inst, fullRedInsts)) {
      instsProcessedByDIFC.add(inst);
      Instruction barrier =
        Call.create1(CALL,
                     null,
                     IRTools.AC(barrierMethod.getOffset()),
                     MethodOperand.STATIC(barrierMethod),
                     Call.getResult(inst).copy());
      insertAndMaybeInline(barrier, inst, ir, false);
      return true;
    }
    return false;
  }

  private Instruction insertAndMaybeInline(Instruction barrier, Instruction inst, IR ir, boolean before) {
    barrier.bcIndex = RUNTIME_SERVICES_BCI;
    barrier.position = inst.position;
    Instruction next = null;
    if (before) {
      inst.insertBefore(barrier);
      next = barrier.prevInstructionInCodeOrder();
    } else {
      inst.insertAfter(barrier);
      // don't need to change next
    }
    // only inline into hot basic blocks
    if (!inst.getBasicBlock().getInfrequent() && !VM.difcNoInlinedBarriers) {
      // only inline stuff barriers that might be outside security regions 
      if ((inst.position != null &&
           !inst.position.getMethod().staticallyInSecureRegion) ||
          VM.difcDynamicBarriers) {
        inline(barrier, ir, true);
      }
    }
    return next;
  }
  
  private boolean redundant(Instruction inst, HashSetRVM<Instruction> fullRedInsts) {
    if (VM.difcNoRedundancyElimination) {
      return false;
    }
    boolean isRedundant = fullRedInsts.contains(inst);
    
    if (DIFC.verbosity >= 2) {
      NormalMethod method = inst.position.getMethod();
      int line = method.getLineNumberForBCIndex(inst.bcIndex);
      System.out.println("[" + isRedundant + "] " + method.getDeclaringClass() + "." + method.getName()  + " : " + line);
      InlineSequence position = inst.position;
      while (position.caller != null) {
        NormalMethod caller = position.caller.getMethod();
        int callerLine = caller.getLineNumberForBCIndex(position.bcIndex);
        System.out.println("     " + caller.getDeclaringClass() + "." + caller.getName()  + " : " + callerLine);
        position = position.caller;
      }
      System.out.println("  " + inst);
    }
    return isRedundant;
  }
  
  /**
   * Inline a call instruction
   */
  private void inline(Instruction inst, IR ir) {
    inline(inst, ir, false);
  }

  /**
   * Inline a call instruction
   */
  private void inline(Instruction inst, IR ir, boolean noCalleeExceptions) {
    // Save and restore inlining control state.
    // Some options have told us to inline this runtime service,
    // so we have to be sure to inline it "all the way" not
    // just 1 level.
    boolean savedInliningOption = ir.options.INLINE;
    boolean savedExceptionOption = ir.options.NO_CALLEE_EXCEPTIONS;
    ir.options.INLINE = true;
    ir.options.NO_CALLEE_EXCEPTIONS = noCalleeExceptions;
    boolean savedOsrGI = ir.options.OSR_GUARDED_INLINING;
    ir.options.OSR_GUARDED_INLINING = false;
    try {
      InlineDecision inlDec =
          InlineDecision.YES(Call.getMethod(inst).getTarget(), "Expansion of runtime service");
      Inliner.execute(inlDec, ir, inst);
    } finally {
      ir.options.INLINE = savedInliningOption;
      ir.options.NO_CALLEE_EXCEPTIONS = savedExceptionOption;
      ir.options.OSR_GUARDED_INLINING = savedOsrGI;
    }
    didSomething = true;
  }

  private final Simple _os = new Simple(1, false, false, false);
  private final BranchOptimizations branchOpts = new BranchOptimizations(-1, true, true);
  private boolean didSomething = false;

  //private final IntConstantOperand IRTools.IC(int x) { return IRTools.IRTools.IC(x); }
  //private final AddressConstantOperand IRTools.AC(Address x) { return IRTools.IRTools.AC(x); }
  //private final AddressConstantOperand IRTools.AC(Offset x) { return IRTools.IRTools.AC(x); }

  // DIFC: awesome redundant barrier elimination
  static final HashSetRVM<Instruction> computeRedundantReadBarriers(IR ir, boolean reads) {
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
          if (New.conforms(i)) {
            useOperand = New.getResult(i);
          } else if (NewArray.conforms(i)) {
            useOperand = NewArray.getResult(i);
          } else if (reads && GetField.conforms(i)) {
            useOperand = GetField.getRef(i);
          } else if (reads && ALoad.conforms(i)) {
            useOperand = ALoad.getArray(i);
          } else if (!reads && PutField.conforms(i)) {
            useOperand = PutField.getRef(i);
          } else if (!reads && AStore.conforms(i)) {
            useOperand = AStore.getArray(i);
          }
          if (useOperand != null) {
            //if (VM.VerifyAssertions) { VM._assert(useOperand.isRegister() || useOperand.isConstant()); }
            if (useOperand.isRegister()) {
              Register useReg = useOperand.asRegister().register;
              if (thisFullRedSet.contains(useReg)) {
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

    // print graph
    //genGraph(ir, "redComp", partRedInsts, fullRedInsts, needsBarrierMap, true);

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
