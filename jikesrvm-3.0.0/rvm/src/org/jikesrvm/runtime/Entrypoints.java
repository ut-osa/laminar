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
package org.jikesrvm.runtime;

import static org.jikesrvm.runtime.EntrypointHelper.getField;
import static org.jikesrvm.runtime.EntrypointHelper.getMethod;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.scheduler.LabelSet;

/**
 * Fields and methods of the virtual machine that are needed by
 * compiler-generated machine code or C runtime code.
 */
public class Entrypoints {
  // The usual causes for getField/Method() to fail are:
  //  1. you mispelled the class name, member name, or member signature
  //  2. the class containing the specified member didn't get compiled
  //

  public static final NormalMethod bootMethod = EntrypointHelper.getMethod(org.jikesrvm.VM.class, "boot", "()V");

  public static final RVMMethod java_lang_Class_forName =
    getMethod(java.lang.Class.class, "forName", "(Ljava/lang/String;)Ljava/lang/Class;");
  public static final RVMMethod java_lang_Class_forName_withLoader =
    getMethod(java.lang.Class.class, "forName", "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;");
  public static final RVMMethod java_lang_reflect_Method_invokeMethod =
      getMethod(java.lang.reflect.Method.class, "invoke",
          "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;");
  public static final RVMMethod getClassFromStackFrame =
    getMethod(org.jikesrvm.classloader.RVMClass.class, "getClassFromStackFrame", "(I)Lorg/jikesrvm/classloader/RVMClass;");
  public static final RVMMethod getClassLoaderFromStackFrame =
    getMethod(org.jikesrvm.classloader.RVMClass.class, "getClassLoaderFromStackFrame", "(I)Ljava/lang/ClassLoader;");

  public static final RVMField magicObjectRemapperField =
      getField(org.jikesrvm.runtime.Magic.class,
               "objectAddressRemapper",
               org.jikesrvm.runtime.ObjectAddressRemapper.class);

  public static final NormalMethod instanceOfMethod =
      getMethod(org.jikesrvm.runtime.RuntimeEntrypoints.class, "instanceOf", "(Ljava/lang/Object;I)Z");
  public static final NormalMethod checkcastMethod =
      getMethod(org.jikesrvm.runtime.RuntimeEntrypoints.class, "checkcast", "(Ljava/lang/Object;I)V");
  public static final NormalMethod checkstoreMethod =
      getMethod(org.jikesrvm.runtime.RuntimeEntrypoints.class, "checkstore", "(Ljava/lang/Object;Ljava/lang/Object;)V");
  public static final NormalMethod athrowMethod =
      getMethod(org.jikesrvm.runtime.RuntimeEntrypoints.class, "athrow", "(Ljava/lang/Throwable;)V");

  // Allocation-related entry points
  //
  public static final NormalMethod resolvedNewScalarMethod =
      getMethod(org.jikesrvm.runtime.RuntimeEntrypoints.class,
                "resolvedNewScalar",
                "(ILorg/jikesrvm/objectmodel/TIB;ZIIII)Ljava/lang/Object;");
  public static final NormalMethod unresolvedNewScalarMethod =
      getMethod(org.jikesrvm.runtime.RuntimeEntrypoints.class, "unresolvedNewScalar", "(II)Ljava/lang/Object;");
  public static final NormalMethod unresolvedNewArrayMethod =
      getMethod(org.jikesrvm.runtime.RuntimeEntrypoints.class, "unresolvedNewArray", "(III)Ljava/lang/Object;");
  public static final NormalMethod resolvedNewArrayMethod =
      getMethod(org.jikesrvm.runtime.RuntimeEntrypoints.class,
                "resolvedNewArray",
                "(IIILorg/jikesrvm/objectmodel/TIB;IIII)Ljava/lang/Object;");

  public static final RVMField sysWriteLockField = getField(org.jikesrvm.VM.class, "sysWriteLock", int.class);
  public static final RVMField intBufferLockField =
      getField(org.jikesrvm.Services.class, "intBufferLock", int.class);
  public static final RVMField dumpBufferLockField =
      getField(org.jikesrvm.Services.class, "dumpBufferLock", int.class);

  public static final NormalMethod unexpectedAbstractMethodCallMethod =
      getMethod(org.jikesrvm.runtime.RuntimeEntrypoints.class, "unexpectedAbstractMethodCall", "()V");
  public static final NormalMethod raiseNullPointerException =
      getMethod(org.jikesrvm.runtime.RuntimeEntrypoints.class, "raiseNullPointerException", "()V");
  public static final NormalMethod raiseArrayBoundsException =
      getMethod(org.jikesrvm.runtime.RuntimeEntrypoints.class, "raiseArrayIndexOutOfBoundsException", "(I)V");
  public static final NormalMethod raiseArithmeticException =
      getMethod(org.jikesrvm.runtime.RuntimeEntrypoints.class, "raiseArithmeticException", "()V");
  public static final NormalMethod raiseAbstractMethodError =
      getMethod(org.jikesrvm.runtime.RuntimeEntrypoints.class, "raiseAbstractMethodError", "()V");
  public static final NormalMethod raiseIllegalAccessError =
      getMethod(org.jikesrvm.runtime.RuntimeEntrypoints.class, "raiseIllegalAccessError", "()V");
  public static final NormalMethod deliverHardwareExceptionMethod =
      getMethod(org.jikesrvm.runtime.RuntimeEntrypoints.class, "deliverHardwareException", "(II)V");
  public static final NormalMethod unlockAndThrowMethod =
      getMethod(org.jikesrvm.runtime.RuntimeEntrypoints.class, "unlockAndThrow", "(Ljava/lang/Object;Ljava/lang/Throwable;)V");

  public static final RVMField gcLockField = getField("Ljava/lang/VMCommonLibrarySupport$GCLock;", "gcLock", int.class);

  public static final NormalMethod invokeInterfaceMethod =
      getMethod(org.jikesrvm.classloader.InterfaceInvocation.class,
                "invokeInterface",
                "(Ljava/lang/Object;I)Lorg/jikesrvm/ArchitectureSpecific$CodeArray;");
  public static final NormalMethod findItableMethod =
      getMethod(org.jikesrvm.classloader.InterfaceInvocation.class,
                "findITable",
                "(Lorg/jikesrvm/objectmodel/TIB;I)Lorg/jikesrvm/objectmodel/ITable;");
  public static final NormalMethod unresolvedInvokeinterfaceImplementsTestMethod =
      getMethod(org.jikesrvm.classloader.InterfaceInvocation.class,
                "unresolvedInvokeinterfaceImplementsTest",
                "(ILjava/lang/Object;)V");

  public static final NormalMethod lockMethod =
      getMethod(org.jikesrvm.objectmodel.ObjectModel.class, "genericLock", "(Ljava/lang/Object;)V");
  public static final NormalMethod unlockMethod =
      getMethod(org.jikesrvm.objectmodel.ObjectModel.class, "genericUnlock", "(Ljava/lang/Object;)V");

  public static final NormalMethod inlineLockMethod =
      getMethod(org.jikesrvm.scheduler.ThinLock.class,
                "inlineLock",
                "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;)V");
  public static final NormalMethod inlineUnlockMethod =
      getMethod(org.jikesrvm.scheduler.ThinLock.class,
                "inlineUnlock",
                "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;)V");

  public static final NormalMethod lazyMethodInvokerMethod =
      getMethod(org.jikesrvm.runtime.DynamicLinker.class, "lazyMethodInvoker", "()V");
  public static final NormalMethod unimplementedNativeMethodMethod =
      getMethod(org.jikesrvm.runtime.DynamicLinker.class, "unimplementedNativeMethod", "()V");
  public static final NormalMethod sysCallMethod =
      getMethod(org.jikesrvm.runtime.DynamicLinker.class, "sysCallMethod", "()V");

  public static final NormalMethod resolveMemberMethod =
      getMethod(org.jikesrvm.classloader.TableBasedDynamicLinker.class, "resolveMember", "(I)I");
  public static final RVMField memberOffsetsField =
      getField(org.jikesrvm.classloader.TableBasedDynamicLinker.class, "memberOffsets", int[].class);

  /** 1L */
  public static final RVMField longOneField = getField(org.jikesrvm.runtime.MathConstants.class, "longOne", long.class);
  /** -1.0F */
  public static final RVMField minusOneField = getField(org.jikesrvm.runtime.MathConstants.class, "minusOne", float.class);
  /** 0.0F */
  public static final RVMField zeroFloatField = getField(org.jikesrvm.runtime.MathConstants.class, "zero", float.class);
  /**0.5F */
  public static final RVMField halfFloatField = getField(org.jikesrvm.runtime.MathConstants.class, "half", float.class);
  /** 1.0F */
  public static final RVMField oneFloatField = getField(org.jikesrvm.runtime.MathConstants.class, "one", float.class);
  /** 2.0F */
  public static final RVMField twoFloatField = getField(org.jikesrvm.runtime.MathConstants.class, "two", float.class);
  /** 2.0F^32 */
  public static final RVMField two32Field = getField(org.jikesrvm.runtime.MathConstants.class, "two32", float.class);
  /** 0.5F^32 */
  public static final RVMField half32Field = getField(org.jikesrvm.runtime.MathConstants.class, "half32", float.class);
  /** 1e-9 */
  public static final RVMField billionthField = getField(org.jikesrvm.runtime.MathConstants.class, "billionth", double.class);
  /** 0.0 */
  public static final RVMField zeroDoubleField = getField(org.jikesrvm.runtime.MathConstants.class, "zeroD", double.class);
  /** 1.0 */
  public static final RVMField oneDoubleField = getField(org.jikesrvm.runtime.MathConstants.class, "oneD", double.class);
  /** largest double that can be rounded to an int */
  public static final RVMField maxintField =
      getField(org.jikesrvm.runtime.MathConstants.class, "maxint", double.class);
  /** largest double that can be rounded to a long */
  public static final RVMField maxlongField =
    getField(org.jikesrvm.runtime.MathConstants.class, "maxlong", double.class);
  /** smallest double that can be rounded to an int */
  public static final RVMField minintField =
      getField(org.jikesrvm.runtime.MathConstants.class, "minint", double.class);
  /** largest float that can be rounded to an int */
  public static final RVMField maxintFloatField =
    getField(org.jikesrvm.runtime.MathConstants.class, "maxintF", float.class);
  /** largest float that can be rounded to a long */
  public static final RVMField maxlongFloatField =
    getField(org.jikesrvm.runtime.MathConstants.class, "maxlongF", float.class);
  /** IEEEmagic constant */
  public static final RVMField IEEEmagicField =
      getField(org.jikesrvm.runtime.MathConstants.class, "IEEEmagic", double.class);
  /** special double value for use in int <--> double conversions */
  public static final RVMField I2DconstantField =
      getField(org.jikesrvm.runtime.MathConstants.class,
               "I2Dconstant",
               double.class);

  public static final RVMField suspendPendingField =
    getField(org.jikesrvm.scheduler.greenthreads.GreenThread.class, "suspendPending", int.class);
  public static final RVMField scratchStorageField =
      getField(org.jikesrvm.scheduler.Processor.class, "scratchStorage", double.class);
  public static final RVMField timeSliceExpiredField =
      getField(org.jikesrvm.scheduler.Processor.class, "timeSliceExpired", int.class);
  public static final RVMField takeYieldpointField =
      getField(org.jikesrvm.scheduler.Processor.class, "takeYieldpoint", int.class);
  public static final RVMField activeThreadField =
      getField(org.jikesrvm.scheduler.Processor.class, "activeThread", org.jikesrvm.scheduler.RVMThread.class);
  public static final RVMField activeThreadStackLimitField =
      getField(org.jikesrvm.scheduler.Processor.class, "activeThreadStackLimit", org.vmmagic.unboxed.Address.class);
  public static final RVMField pthreadIDField = getField(org.jikesrvm.scheduler.Processor.class, "pthread_id", int.class);
  public static final RVMField timerTicksField =
    getField(org.jikesrvm.scheduler.greenthreads.GreenProcessor.class, "timerTicks", int.class);
  public static final RVMField reportedTimerTicksField =
      getField(org.jikesrvm.scheduler.greenthreads.GreenProcessor.class, "reportedTimerTicks", int.class);
  public static final RVMField vpStatusField = getField(org.jikesrvm.scheduler.Processor.class, "vpStatus", int.class);
  public static final RVMField threadIdField = getField(org.jikesrvm.scheduler.Processor.class, "threadId", int.class);

  public static final RVMField referenceReferentField =
      getField(java.lang.ref.Reference.class, "_referent", org.vmmagic.unboxed.Address.class);

  /** Used in deciding which stack frames we can elide when printing. */
  public static final NormalMethod mainThreadRunMethod =
      getMethod(org.jikesrvm.scheduler.MainThread.class, "run", "()V");

  public static final NormalMethod yieldpointFromPrologueMethod =
      getMethod(org.jikesrvm.scheduler.RVMThread.class, "yieldpointFromPrologue", "()V");
  public static final NormalMethod yieldpointFromBackedgeMethod =
      getMethod(org.jikesrvm.scheduler.RVMThread.class, "yieldpointFromBackedge", "()V");
  public static final NormalMethod yieldpointFromEpilogueMethod =
      getMethod(org.jikesrvm.scheduler.RVMThread.class, "yieldpointFromEpilogue", "()V");

  public static final NormalMethod threadRunMethod = getMethod(org.jikesrvm.scheduler.RVMThread.class, "run", "()V");
  public static final NormalMethod threadStartoffMethod =
      getMethod(org.jikesrvm.scheduler.RVMThread.class, "startoff", "()V");
  public static final RVMField threadStackField = getField(org.jikesrvm.scheduler.RVMThread.class, "stack", byte[].class);
  public static final RVMField stackLimitField =
      getField(org.jikesrvm.scheduler.RVMThread.class, "stackLimit", org.vmmagic.unboxed.Address.class);

  public static final RVMField beingDispatchedField =
      getField(org.jikesrvm.scheduler.RVMThread.class, "beingDispatched", boolean.class);
  public static final RVMField threadSlotField = getField(org.jikesrvm.scheduler.RVMThread.class, "threadSlot", int.class);
  public static final RVMField jniEnvField =
      getField(org.jikesrvm.scheduler.RVMThread.class, "jniEnv", org.jikesrvm.jni.JNIEnvironment.class);
  public static final RVMField threadContextRegistersField =
      getField(org.jikesrvm.scheduler.RVMThread.class,
               "contextRegisters",
               org.jikesrvm.ArchitectureSpecific.Registers.class);
  public static final RVMField threadExceptionRegistersField =
      getField(org.jikesrvm.scheduler.RVMThread.class,
               "exceptionRegisters",
               org.jikesrvm.ArchitectureSpecific.Registers.class);

  public static final RVMField tracePrevAddressField =
      getField(org.jikesrvm.objectmodel.MiscHeader.class, "prevAddress", org.vmmagic.unboxed.Word.class);
  public static final RVMField traceOIDField =
      getField(org.jikesrvm.objectmodel.MiscHeader.class, "oid", org.vmmagic.unboxed.Word.class);
  public static final RVMField dispenserField = getField(org.jikesrvm.mm.mmtk.Lock.class, "dispenser", int.class);
  public static final RVMField servingField = getField(org.jikesrvm.mm.mmtk.Lock.class, "serving", int.class);
  public static final RVMField lockThreadField =
      getField(org.jikesrvm.mm.mmtk.Lock.class, "thread", org.jikesrvm.scheduler.RVMThread.class);
  public static final RVMField gcStatusField = getField(org.mmtk.plan.Plan.class, "gcStatus", int.class);
  public static final RVMField SQCFField = getField(org.mmtk.utility.deque.SharedDeque.class, "completionFlag", int.class);
  public static final RVMField SQNCField = getField(org.mmtk.utility.deque.SharedDeque.class, "numConsumers", int.class);
  public static final RVMField SQNCWField =
      getField(org.mmtk.utility.deque.SharedDeque.class, "numConsumersWaiting", int.class);
  public static final RVMField SQheadField =
      getField(org.mmtk.utility.deque.SharedDeque.class, "head", org.vmmagic.unboxed.Address.class);
  public static final RVMField SQtailField =
      getField(org.mmtk.utility.deque.SharedDeque.class, "tail", org.vmmagic.unboxed.Address.class);
  public static final RVMField SQBEField = getField(org.mmtk.utility.deque.SharedDeque.class, "bufsenqueued", int.class);
  public static final RVMField synchronizedCounterField =
      getField(org.jikesrvm.mm.mmtk.SynchronizedCounter.class, "count", int.class);

  public static final NormalMethod arrayStoreWriteBarrierMethod =
      getMethod(org.jikesrvm.mm.mminterface.MemoryManager.class, "arrayStoreWriteBarrier", "(Ljava/lang/Object;ILjava/lang/Object;)V");
  public static final NormalMethod putfieldWriteBarrierMethod =
      getMethod(org.jikesrvm.mm.mminterface.MemoryManager.class, "putfieldWriteBarrier", "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;Ljava/lang/Object;I)V");
  public static final NormalMethod putstaticWriteBarrierMethod =
      getMethod(org.jikesrvm.mm.mminterface.MemoryManager.class, "putstaticWriteBarrier", "(Lorg/vmmagic/unboxed/Offset;Ljava/lang/Object;I)V");

  public static final NormalMethod arrayLoadReadBarrierMethod =
      getMethod(org.jikesrvm.mm.mminterface.MemoryManager.class, "arrayLoadReadBarrier", "(Ljava/lang/Object;I)Ljava/lang/Object;");
  public static final NormalMethod getfieldReadBarrierMethod =
      getMethod(org.jikesrvm.mm.mminterface.MemoryManager.class, "getfieldReadBarrier", "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;I)Ljava/lang/Object;");
  public static final NormalMethod getstaticReadBarrierMethod =
      getMethod(org.jikesrvm.mm.mminterface.MemoryManager.class, "getstaticReadBarrier", "(Lorg/vmmagic/unboxed/Offset;I)Ljava/lang/Object;");

  // DIFC: barriers

  public static final NormalMethod difcReadBarrierInsideSRMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "readBarrierInsideSR", "(Ljava/lang/Object;)V");
  public static final NormalMethod difcReadBarrierOutsideSRMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "readBarrierOutsideSR", "(Ljava/lang/Object;)V");
  public static final NormalMethod difcReadBarrierDynamicMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "readBarrierDynamic", "(Ljava/lang/Object;)V");
  
  public static final NormalMethod difcWriteBarrierInsideSRMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "writeBarrierInsideSR", "(Ljava/lang/Object;)V");
  public static final NormalMethod difcWriteBarrierOutsideSRMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "writeBarrierOutsideSR", "(Ljava/lang/Object;)V");
  public static final NormalMethod difcWriteBarrierDynamicMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "writeBarrierDynamic", "(Ljava/lang/Object;)V");
  
  public static final NormalMethod difcStaticReadBarrierInsideSRMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "staticReadBarrierInsideSR", "(I)V");
  /*public static final NormalMethod difcStaticReadBarrierOutsideSRMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "staticReadBarrierOutsideSR", "(I)V");*/
  public static final NormalMethod difcStaticReadBarrierDynamicMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "staticReadBarrierDynamic", "(I)V");
  
  public static final NormalMethod difcStaticWriteBarrierInsideSRMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "staticWriteBarrierInsideSR", "(I)V");
  /*public static final NormalMethod difcStaticWriteBarrierOutsideSRMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "staticWriteBarrierOutsideSR", "(I)V");*/
  public static final NormalMethod difcStaticWriteBarrierDynamicMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "staticWriteBarrierDynamic", "(I)V");
  
  public static final NormalMethod difcAllocBarrierInsideSRMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "allocBarrierInsideSR", "(Ljava/lang/Object;)V");
  /*public static final NormalMethod difcAllocBarrierOutsideSRMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "allocBarrierOutsideSR", "(Ljava/lang/Object;)V");*/
  public static final NormalMethod difcAllocBarrierDynamicMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "allocBarrierDynamic", "(Ljava/lang/Object;)V");
  
  public static final NormalMethod difcReadBarrierInsideSRDebugMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "readBarrierInsideSRDebug", "(Ljava/lang/Object;)V");
  public static final NormalMethod difcReadBarrierOutsideSRDebugMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "readBarrierOutsideSRDebug", "(Ljava/lang/Object;)V");
  public static final NormalMethod difcReadBarrierDynamicDebugMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "readBarrierDynamicDebug", "(Ljava/lang/Object;)V");
  
  public static final NormalMethod difcWriteBarrierInsideSRDebugMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "writeBarrierInsideSRDebug", "(Ljava/lang/Object;)V");
  public static final NormalMethod difcWriteBarrierOutsideSRDebugMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "writeBarrierOutsideSRDebug", "(Ljava/lang/Object;)V");
  public static final NormalMethod difcWriteBarrierDynamicDebugMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "writeBarrierDynamicDebug", "(Ljava/lang/Object;)V");
  
  public static final NormalMethod difcStaticReadBarrierInsideSRDebugMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "staticReadBarrierInsideSRDebug", "(I)V");
  public static final NormalMethod difcStaticReadBarrierOutsideSRDebugMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "staticReadBarrierOutsideSRDebug", "(I)V");
  public static final NormalMethod difcStaticReadBarrierDynamicDebugMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "staticReadBarrierDynamicDebug", "(I)V");
  
  public static final NormalMethod difcStaticWriteBarrierInsideSRDebugMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "staticWriteBarrierInsideSRDebug", "(I)V");
  public static final NormalMethod difcStaticWriteBarrierOutsideSRDebugMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "staticWriteBarrierOutsideSRDebug", "(I)V");
  public static final NormalMethod difcStaticWriteBarrierDynamicDebugMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "staticWriteBarrierDynamicDebug", "(I)V");
  
  public static final NormalMethod difcAllocBarrierInsideSRDebugMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "allocBarrierInsideSRDebug", "(Ljava/lang/Object;)V");
  public static final NormalMethod difcAllocBarrierOutsideSRDebugMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "allocBarrierOutsideSRDebug", "(Ljava/lang/Object;)V");
  public static final NormalMethod difcAllocBarrierDynamicDebugMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "allocBarrierDynamicDebug", "(Ljava/lang/Object;)V");

  // Airavat: barriers

  public static final NormalMethod airavatReadBarrierInsideSRMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "airavatReadBarrierInsideSR", "(Ljava/lang/Object;)V");
  /*public static final NormalMethod airavatReadBarrierOutsideSRMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "airavatReadBarrierOutsideSR", "(Ljava/lang/Object;)V");*/
  public static final NormalMethod airavatReadBarrierDynamicMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "airavatReadBarrierDynamic", "(Ljava/lang/Object;)V");
  
  public static final NormalMethod airavatWriteBarrierInsideSRMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "airavatWriteBarrierInsideSR", "(Ljava/lang/Object;)V");
  /*public static final NormalMethod airavatWriteBarrierOutsideSRMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "airavatWriteBarrierOutsideSR", "(Ljava/lang/Object;)V");*/
  public static final NormalMethod airavatWriteBarrierDynamicMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "airavatWriteBarrierDynamic", "(Ljava/lang/Object;)V");
  
  public static final NormalMethod airavatStaticReadBarrierInsideSRMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "airavatStaticReadBarrierInsideSR", "(I)V");
  /*public static final NormalMethod airavatStaticReadBarrierOutsideSRMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "airavatStaticReadBarrierOutsideSR", "(I)V");*/
  public static final NormalMethod airavatStaticReadBarrierDynamicMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "airavatStaticReadBarrierDynamic", "(I)V");
  
  public static final NormalMethod airavatStaticWriteBarrierInsideSRMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "airavatStaticWriteBarrierInsideSR", "(I)V");
  /*public static final NormalMethod airavatStaticWriteBarrierOutsideSRMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "airavatStaticWriteBarrierOutsideSR", "(I)V");*/
  public static final NormalMethod airavatStaticWriteBarrierDynamicMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "airavatStaticWriteBarrierDynamic", "(I)V");
  
  public static final NormalMethod airavatAllocBarrierInsideSRMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "airavatAllocBarrierInsideSR", "(Ljava/lang/Object;)V");
  /*public static final NormalMethod airavatAllocBarrierOutsideSRMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "airavatAllocBarrierOutsideSR", "(Ljava/lang/Object;)V");*/
  public static final NormalMethod airavatAllocBarrierDynamicMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "airavatAllocBarrierDynamic", "(Ljava/lang/Object;)V");
  
  public static final NormalMethod airavatReadBarrierInsideSRDebugMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "airavatReadBarrierInsideSRDebug", "(Ljava/lang/Object;)V");
  public static final NormalMethod airavatReadBarrierOutsideSRDebugMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "airavatReadBarrierOutsideSRDebug", "(Ljava/lang/Object;)V");
  public static final NormalMethod airavatReadBarrierDynamicDebugMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "airavatReadBarrierDynamicDebug", "(Ljava/lang/Object;)V");
  
  public static final NormalMethod airavatWriteBarrierInsideSRDebugMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "airavatWriteBarrierInsideSRDebug", "(Ljava/lang/Object;)V");
  public static final NormalMethod airavatWriteBarrierOutsideSRDebugMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "airavatWriteBarrierOutsideSRDebug", "(Ljava/lang/Object;)V");
  public static final NormalMethod airavatWriteBarrierDynamicDebugMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "airavatWriteBarrierDynamicDebug", "(Ljava/lang/Object;)V");
  
  public static final NormalMethod airavatStaticReadBarrierInsideSRDebugMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "airavatStaticReadBarrierInsideSRDebug", "(I)V");
  public static final NormalMethod airavatStaticReadBarrierOutsideSRDebugMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "airavatStaticReadBarrierOutsideSRDebug", "(I)V");
  public static final NormalMethod airavatStaticReadBarrierDynamicDebugMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "airavatStaticReadBarrierDynamicDebug", "(I)V");
  
  public static final NormalMethod airavatStaticWriteBarrierInsideSRDebugMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "airavatStaticWriteBarrierInsideSRDebug", "(I)V");
  public static final NormalMethod airavatStaticWriteBarrierOutsideSRDebugMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "airavatStaticWriteBarrierOutsideSRDebug", "(I)V");
  public static final NormalMethod airavatStaticWriteBarrierDynamicDebugMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "airavatStaticWriteBarrierDynamicDebug", "(I)V");
  
  public static final NormalMethod airavatAllocBarrierInsideSRDebugMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "airavatAllocBarrierInsideSRDebug", "(Ljava/lang/Object;)V");
  public static final NormalMethod airavatAllocBarrierOutsideSRDebugMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "airavatAllocBarrierOutsideSRDebug", "(Ljava/lang/Object;)V");
  public static final NormalMethod airavatAllocBarrierDynamicDebugMethod =
    getMethod(org.jikesrvm.scheduler.DIFC.class, "airavatAllocBarrierDynamicDebug", "(Ljava/lang/Object;)V");

  
  public static final NormalMethod modifyCheckMethod =
      getMethod(org.jikesrvm.mm.mminterface.MemoryManager.class, "modifyCheck", "(Ljava/lang/Object;)V");

  public static final RVMField outputLockField = getField(org.jikesrvm.scheduler.Scheduler.class, "outputLock", int.class);

  // used in boot image writer
  public static final RVMField greenProcessorsField =
      getField(org.jikesrvm.scheduler.greenthreads.GreenScheduler.class, "processors", org.jikesrvm.scheduler.ProcessorTable.class);
  public static final RVMField debugRequestedField =
      getField(org.jikesrvm.scheduler.Scheduler.class, "debugRequested", boolean.class);
  public static final NormalMethod dumpStackAndDieMethod =
      getMethod(org.jikesrvm.scheduler.Scheduler.class, "dumpStackAndDie", "(Lorg/vmmagic/unboxed/Address;)V");

  public static final RVMField latestContenderField =
      getField(org.jikesrvm.scheduler.ProcessorLock.class, "latestContender", org.jikesrvm.scheduler.Processor.class);

  public static final RVMField depthField = getField(org.jikesrvm.classloader.RVMType.class, "depth", int.class);
  public static final RVMField idField = getField(org.jikesrvm.classloader.RVMType.class, "id", int.class);
  public static final RVMField dimensionField = getField(org.jikesrvm.classloader.RVMType.class, "dimension", int.class);

  public static final RVMField innermostElementTypeDimensionField =
      getField(org.jikesrvm.classloader.RVMArray.class, "innermostElementTypeDimension", int.class);

  public static final RVMField JNIEnvSavedPRField =
      getField(org.jikesrvm.jni.JNIEnvironment.class, "savedPRreg", org.jikesrvm.scheduler.Processor.class);
  public static final RVMField JNIGlobalRefsField =
    getField(org.jikesrvm.jni.JNIGlobalRefTable.class, "JNIGlobalRefs", org.vmmagic.unboxed.AddressArray.class);
  public static final RVMField JNIRefsField =
      getField(org.jikesrvm.jni.JNIEnvironment.class, "JNIRefs", org.vmmagic.unboxed.AddressArray.class);
  public static final RVMField JNIRefsTopField = getField(org.jikesrvm.jni.JNIEnvironment.class, "JNIRefsTop", int.class);
  public static final RVMField JNIRefsMaxField = getField(org.jikesrvm.jni.JNIEnvironment.class, "JNIRefsMax", int.class);
  public static final RVMField JNIRefsSavedFPField =
      getField(org.jikesrvm.jni.JNIEnvironment.class, "JNIRefsSavedFP", int.class);
  public static final RVMField JNITopJavaFPField =
      getField(org.jikesrvm.jni.JNIEnvironment.class, "JNITopJavaFP", org.vmmagic.unboxed.Address.class);
  public static final RVMField JNIPendingExceptionField =
      getField(org.jikesrvm.jni.JNIEnvironment.class, "pendingException", java.lang.Throwable.class);
  public static final RVMField JNIExternalFunctionsField =
      getField(org.jikesrvm.jni.JNIEnvironment.class, "externalJNIFunctions", org.vmmagic.unboxed.Address.class);
  public static final RVMField JNIEnvSavedJTOCField =
      (VM.BuildForPowerPC) ? getField(org.jikesrvm.jni.JNIEnvironment.class,
                                      "savedJTOC",
                                      org.vmmagic.unboxed.Address.class) : null;

  public static final RVMField the_boot_recordField =
      getField(org.jikesrvm.runtime.BootRecord.class, "the_boot_record", org.jikesrvm.runtime.BootRecord.class);
  public static final RVMField sysVirtualProcessorYieldIPField =
      getField(org.jikesrvm.runtime.BootRecord.class, "sysVirtualProcessorYieldIP", org.vmmagic.unboxed.Address.class);
  public static final RVMField externalSignalFlagField =
      getField(org.jikesrvm.runtime.BootRecord.class, "externalSignalFlag", int.class);
  public static final RVMField sysLongDivideIPField =
      getField(org.jikesrvm.runtime.BootRecord.class, "sysLongDivideIP", org.vmmagic.unboxed.Address.class);
  public static final RVMField sysLongRemainderIPField =
      getField(org.jikesrvm.runtime.BootRecord.class, "sysLongRemainderIP", org.vmmagic.unboxed.Address.class);
  public static final RVMField sysLongToFloatIPField =
      getField(org.jikesrvm.runtime.BootRecord.class, "sysLongToFloatIP", org.vmmagic.unboxed.Address.class);
  public static final RVMField sysLongToDoubleIPField =
      getField(org.jikesrvm.runtime.BootRecord.class, "sysLongToDoubleIP", org.vmmagic.unboxed.Address.class);
  public static final RVMField sysFloatToIntIPField =
      getField(org.jikesrvm.runtime.BootRecord.class, "sysFloatToIntIP", org.vmmagic.unboxed.Address.class);
  public static final RVMField sysDoubleToIntIPField =
      getField(org.jikesrvm.runtime.BootRecord.class, "sysDoubleToIntIP", org.vmmagic.unboxed.Address.class);
  public static final RVMField sysFloatToLongIPField =
      getField(org.jikesrvm.runtime.BootRecord.class, "sysFloatToLongIP", org.vmmagic.unboxed.Address.class);
  public static final RVMField sysDoubleToLongIPField =
      getField(org.jikesrvm.runtime.BootRecord.class, "sysDoubleToLongIP", org.vmmagic.unboxed.Address.class);
  public static final RVMField sysDoubleRemainderIPField =
      getField(org.jikesrvm.runtime.BootRecord.class, "sysDoubleRemainderIP", org.vmmagic.unboxed.Address.class);

  public static final RVMField edgeCountersField =
      getField(org.jikesrvm.compilers.baseline.EdgeCounts.class, "data", int[][].class);

  public static final RVMField inetAddressAddressField = VM.BuildForGnuClasspath ?
      getField(java.net.InetAddress.class, "address", int.class) : null;
  public static final RVMField inetAddressFamilyField = VM.BuildForGnuClasspath ?
      getField(java.net.InetAddress.class, "family", int.class) : null;

  public static final RVMField socketImplAddressField =
      getField(java.net.SocketImpl.class, "address", java.net.InetAddress.class);
  public static final RVMField socketImplPortField = getField(java.net.SocketImpl.class, "port", int.class);

  //////////////////
  // Entrypoints that are valid only when the opt compiler is included in the build
  //////////////////
  public static final RVMField specializedMethodsField;

  public static final RVMField osrOrganizerQueueLockField;
  public static final NormalMethod optThreadSwitchFromOsrOptMethod;
  public static final NormalMethod optThreadSwitchFromPrologueMethod;
  public static final NormalMethod optThreadSwitchFromBackedgeMethod;
  public static final NormalMethod optThreadSwitchFromEpilogueMethod;
  public static final NormalMethod yieldpointFromNativePrologueMethod;
  public static final NormalMethod yieldpointFromNativeEpilogueMethod;
  public static final NormalMethod optResolveMethod;
  public static final NormalMethod optNewArrayArrayMethod;
  public static final NormalMethod optNew2DArrayMethod;
  public static final NormalMethod sysArrayCopy;

  static {
    if (VM.BuildForOptCompiler) {
      specializedMethodsField =
          getField(org.jikesrvm.compilers.opt.specialization.SpecializedMethodPool.class,
                   "specializedMethods",
                   org.jikesrvm.ArchitectureSpecific.CodeArray[].class);
      osrOrganizerQueueLockField = getField(org.jikesrvm.adaptive.OSROrganizerThread.class, "queueLock", int.class);
      optThreadSwitchFromOsrOptMethod =
          getMethod(org.jikesrvm.compilers.opt.runtimesupport.OptSaveVolatile.class, "yieldpointFromOsrOpt", "()V");
      optThreadSwitchFromPrologueMethod =
          getMethod(org.jikesrvm.compilers.opt.runtimesupport.OptSaveVolatile.class, "yieldpointFromPrologue", "()V");
      optThreadSwitchFromBackedgeMethod =
          getMethod(org.jikesrvm.compilers.opt.runtimesupport.OptSaveVolatile.class, "yieldpointFromBackedge", "()V");
      optThreadSwitchFromEpilogueMethod =
          getMethod(org.jikesrvm.compilers.opt.runtimesupport.OptSaveVolatile.class, "yieldpointFromEpilogue", "()V");
      yieldpointFromNativePrologueMethod =
          getMethod(org.jikesrvm.compilers.opt.runtimesupport.OptSaveVolatile.class, "yieldpointFromNativePrologue", "()V");
      yieldpointFromNativeEpilogueMethod =
          getMethod(org.jikesrvm.compilers.opt.runtimesupport.OptSaveVolatile.class, "yieldpointFromNativeEpilogue", "()V");
      optResolveMethod = getMethod(org.jikesrvm.compilers.opt.runtimesupport.OptSaveVolatile.class, "resolve", "()V");

      optNewArrayArrayMethod =
          getMethod(org.jikesrvm.compilers.opt.runtimesupport.OptLinker.class, "newArrayArray", "(I[II)Ljava/lang/Object;");
      optNew2DArrayMethod =
          getMethod(org.jikesrvm.compilers.opt.runtimesupport.OptLinker.class, "new2DArray", "(IIII)Ljava/lang/Object;");
      sysArrayCopy = getMethod("Ljava/lang/VMCommonLibrarySupport;", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V");
      sysArrayCopy.setRuntimeServiceMethod(false);
    } else {
      specializedMethodsField = null;
      osrOrganizerQueueLockField = null;
      optThreadSwitchFromOsrOptMethod = null;
      optThreadSwitchFromPrologueMethod = null;
      optThreadSwitchFromBackedgeMethod = null;
      optThreadSwitchFromEpilogueMethod = null;
      yieldpointFromNativePrologueMethod = null;
      yieldpointFromNativeEpilogueMethod = null;
      optResolveMethod = null;
      optNewArrayArrayMethod = null;
      optNew2DArrayMethod = null;
      sysArrayCopy = null;
    }
  }

  public static final RVMField classLoaderDefinedPackages =
    getField(java.lang.ClassLoader.class, "definedPackages", java.util.HashMap.class);

  public static final RVMField luni1;
  public static final RVMField luni2;
  public static final RVMField luni3;
  public static final RVMField luni4;
  public static final RVMField luni5;

  static {
    if (VM.BuildForHarmony) {
      luni1 = getField("Lorg/apache/harmony/luni/util/Msg;", "bundle", java.util.ResourceBundle.class);
      luni2 = getField("Lorg/apache/harmony/archive/internal/nls/Messages;", "bundle", java.util.ResourceBundle.class);
      luni3 = getField("Lorg/apache/harmony/luni/internal/nls/Messages;", "bundle", java.util.ResourceBundle.class);
      luni4 = getField("Lorg/apache/harmony/nio/internal/nls/Messages;", "bundle", java.util.ResourceBundle.class);
      luni5 = getField("Lorg/apache/harmony/niochar/internal/nls/Messages;", "bundle", java.util.ResourceBundle.class);
    } else {
      luni1 = null;
      luni2 = null;
      luni3 = null;
      luni4 = null;
      luni5 = null;
    }
  }
}
