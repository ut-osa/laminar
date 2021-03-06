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
package org.mmtk.harness;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

import org.mmtk.harness.lang.AllocationSite;
import org.mmtk.harness.lang.Trace;
import org.mmtk.harness.lang.Trace.Item;
import org.mmtk.harness.vm.ActivePlan;
import org.mmtk.harness.vm.ObjectModel;
import org.mmtk.plan.MutatorContext;
import org.mmtk.plan.Plan;
import org.mmtk.plan.TraceLocal;
import org.mmtk.vm.Collection;
import org.mmtk.vm.VM;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;

/**
 * This class represents a mutator thread that has memory managed by MMTk.
 *
 * From within the context of this thread it is possible to call the muXXX methods
 * to test MMTk.
 *
 * To get the current Mutator (from a context where this is valid) it is possible to
 * call Mutator.current().
 *
 * Note that as soon as the mutator is created it is considered active. This means
 * that a GC can not occur unless you execute commands on the mutator (or muEnd it).
 */
public class Mutator extends MMTkThread {
  private static boolean gcEveryWB = false;

  public static void setGcEveryWB() {
    gcEveryWB = true;
  }

  /** Registered mutators */
  protected static ArrayList<Mutator> mutators = new ArrayList<Mutator>();

  /**
   * Get a mutator by id.
   */
  public static Mutator get(int id) {
    return mutators.get(id);
  }

  /**
   * Get the currently executing mutator.
   */
  public static Mutator current() {
    assert Thread.currentThread() instanceof Mutator  : "Current thread does is not a Mutator";
    return (Mutator)Thread.currentThread();
  }

  /**
   * Return the number of mutators that have been created.
   */
  public static int count() {
    return mutators.size();
  }

  /**
   * Register a mutator, returning the allocated id.
   */
  public static synchronized int register(MutatorContext context) {
    int id = mutators.size();
    mutators.add(null);
    return id;
  }

  /** Is this thread out of memory if the gc cannot free memory */
  private boolean outOfMemory;

  /** Get the out of memory status */
  public boolean isOutOfMemory() {
    return outOfMemory;
  }

  /** Set the out of memory status */
  public void setOutOfMemory(boolean value) {
    outOfMemory = value;
  }

  /** The number of collection attempts this thread has had since allocation succeeded */
  private int collectionAttempts;

  /** Get the number of collection attempts */
  public int getCollectionAttempts() {
    return collectionAttempts;
  }

  /** Report a collection attempt */
  public void reportCollectionAttempt() {
    collectionAttempts++;
  }

  /** Clear the collection attempts */
  public void clearCollectionAttempts() {
    collectionAttempts = 0;
  }

  /** Was the last failure a physical allocation failure (rather than a budget failure) */
  private boolean physicalAllocationFailure;

  /** Was the last failure a physical allocation failure */
  public boolean isPhysicalAllocationFailure() {
    return physicalAllocationFailure;
  }

  /** Set if last failure a physical allocation failure */
  public void setPhysicalAllocationFailure(boolean value) {
    physicalAllocationFailure = value;
  }

  /** The MMTk MutatorContext for this mutator */
  protected final MutatorContext context;

  /**
   * The type of exception that is expected at the end of execution.
   */
  private Class<?> expectedThrowable;

  /**
   * Set an expectation that the execution will exit with a throw of this exception.
   */
  public void setExpectedThrowable(Class<?> expectedThrowable) {
    this.expectedThrowable = expectedThrowable;
  }

  /**
   * Create a mutator thread, specifying an (optional) entry point and initial local variable map.
   *
   * @param entryPoint The entryPoint.
   * @param locals The local variable map.
   */
  public Mutator(Runnable entryPoint) {
    super(entryPoint);
    try {
      String prefix = Harness.plan.getValue();
      this.context = (MutatorContext)Class.forName(prefix + "Mutator").newInstance();
      this.context.initMutator();
    } catch (Exception ex) {
      throw new RuntimeException("Could not create Mutator", ex);
    }
    mutators.set(context.getId(), this);
    setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
      public void uncaughtException(Thread t, Throwable e) {
        if (e.getClass() == expectedThrowable) {
          System.err.println("Mutator " + context.getId() + " exiting due to expected exception of class " + expectedThrowable);
          expectedThrowable = null;
          end();
        } else {
          System.err.print("Mutator " + context.getId() + " caused unexpected exception: ");
          e.printStackTrace();
          System.exit(1);
        }
      }
    });
  }

  /**
   * Create a new mutator thread. Intended to be used for subclasses implementing a run() method.
   */
  protected Mutator() {
    this(null);
  }

  /**
   * Return the MMTk MutatorContext for this mutator.
   */
  public MutatorContext getContext() {
    return context;
  }

  /**
   * Compute the thread roots for this mutator.
   */
  public void computeThreadRoots(TraceLocal trace) {
    // Nothing to do for the default mutator
  }

  /**
   * Print the thread roots and add them to a stack for processing.
   */
  public void dumpThreadRoots(int width, Stack<ObjectReference> roots) {
    // Nothing to do for the default mutator
  }

  /**
   * Format the object for dumping.
   */
  public static String formatObject(ObjectReference object) {
//    return Address.fromIntZeroExtend(object.isNull() ? 0 : ObjectModel.getId(object)).toString();
    return String.format("%s[%d@%s]", object, ObjectModel.getId(object), getSiteName(object));
  }

  /**
   * Format the object for dumping, and trim to a max width.
   */
  public static String formatObject(int width, ObjectReference object) {
    String base = formatObject(object);
    return base.substring(base.length() - width);
  }

  /**
   * Print the thread roots and add them to a stack for processing.
   */
  public static void dumpHeap() {
    int width = Integer.toHexString(ObjectModel.nextObjectId()).length();
    Stack<ObjectReference> workStack = new Stack<ObjectReference>();
    Set<ObjectReference> dumped = new HashSet<ObjectReference>();
    for(Mutator m: mutators) {
      System.err.println("Mutator " + m.context.getId());
      m.dumpThreadRoots(width, workStack);
    }
    System.err.println("Heap (Depth First)");
    while(!workStack.isEmpty()) {
      ObjectReference object = workStack.pop();
      if (!dumped.contains(object)) {
        dumped.add(object);
        ObjectModel.dumpLogicalObject(width, object, workStack);
      }
    }
  }

/**
   * A gc safe point for the mutator.
   */
  public boolean gcSafePoint() {
    if (Collector.gcTriggered()) {
      waitForGC();
      return true;
    }
    return false;
  }

  /**
   * An out of memory error originating from within MMTk.
   *
   * Tests that try to exercise out of memory conditions can catch this exception.
   */
  public static class OutOfMemory extends RuntimeException {
    public static final long serialVersionUID = 1;
  }

  /**
   * Object used for synchronizing the number of mutators waiting for a gc.
   */
  private static Object count = new Object();

  /**
   * The number of mutators waiting for a collection to proceed.
   */
  private static int mutatorsWaitingForGC;

  /**
   * The number of mutators currently executing in the system.
   */
  private static int activeMutators;

  /**
   * Mark a mutator as currently active. If a GC is currently in process we must
   * wait for it to finish.
   */
  protected static void begin() {
    synchronized (count) {
      if (!allWaitingForGC()) {
        activeMutators++;
        return;
      }
      mutatorsWaitingForGC++;
    }
    Collector.waitForGC(false);
    synchronized (count) {
      mutatorsWaitingForGC--;
      activeMutators++;
    }
  }

  /**
   * A mutator is creating a new mutator and calling begin on its behalf.
   * This simplfies the logic and is guaranteed not to block for GC.
   */
  public void beginChild() {
    synchronized (count) {
      if (!allWaitingForGC()) {
        activeMutators++;
        return;
      }
    }
    notReached();
  }

  /**
   * Mark a mutator as no longer active. If a GC has been triggered we must ensure
   * that it proceeds before we deactivate.
   */
  protected void end() {
    check(expectedThrowable == null, "Expected exception of class " + expectedThrowable + " not found");
    boolean lastToGC;
    synchronized (count) {
      lastToGC = (mutatorsWaitingForGC == (activeMutators - 1));
      if (!lastToGC) {
        activeMutators--;
        return;
      }
      mutatorsWaitingForGC++;
    }
    Collector.waitForGC(lastToGC);
    synchronized (count) {
        mutatorsWaitingForGC--;
        activeMutators--;
    }
  }

  /**
   * Are all active mutators waiting for GC?
   */
  public static boolean allWaitingForGC() {
    return mutatorsWaitingForGC == activeMutators;
  }

  /**
   * Cause the current thread to wait for a triggered GC to proceed.
   */
  public void waitForGC() {
    boolean allWaiting;
    synchronized (count) {
      mutatorsWaitingForGC++;
      allWaiting = allWaitingForGC();
    }
    Collector.waitForGC(allWaiting);
    synchronized (count) {
        mutatorsWaitingForGC--;
    }
  }

  /**
   * Request a heap dump (also invokes a garbage collection)
   */
  public void heapDump() {
    Collector.requestHeapDump();
    gc();
  }

  /**
   * Request a garbage collection.
   */
  public void gc() {
    VM.collection.triggerCollection(Collection.EXTERNAL_GC_TRIGGER);
  }

  /**
   * Fail during execution.
   * @param failMessage
   */
  public void fail(String failMessage) {
    check(false, failMessage);
  }

  /**
   * Print a message that this code path should be impossible and exit.
   */
  public void notReached() {
    check(false, "Unreachable code reached!");
  }

  /**
   * Assert that the given condition is true or print the failure message.
   */
  public void check(boolean condition, String failMessage) {
    if (!condition) throw new RuntimeException(failMessage);
  }

  /**
   * Store a value into the data field of an object.
   *
   * @param object The object to store the field of.
   * @param index The field index.
   * @param value The value to store.
   */
  public void storeDataField(ObjectReference object, int index, int value) {
    int limit = ObjectModel.getDataCount(object);
    check(!object.isNull(), "Object can not be null");
    check(index >= 0, "Index must be non-negative");
    check(index < limit, "Index out of bounds");

    Address ref = ObjectModel.getDataSlot(object, index);
    ref.store(value);
    Trace.trace(Item.STORE,"%s.[%d] = %d",object.toString(),index,value);
  }

  /**
   * Store a value into a reference field of an object.
   *
   * @param object The object to store the field of.
   * @param index The field index.
   * @param value The value to store.
   */
  public void storeReferenceField(ObjectReference object, int index, ObjectReference value) {
    int limit = ObjectModel.getRefs(object);
    if (Trace.isEnabled(Item.STORE)) {
      System.err.printf("[%s].object[%d/%d] = %s%n",object.toString(),index,limit,value.toString());
    }
    check(!object.isNull(), "Object can not be null");
    check(index >= 0, "Index must be non-negative");
    check(index < limit, "Index out of bounds");

    Address referenceSlot = ObjectModel.getRefSlot(object, index);
    if (ActivePlan.constraints.needsWriteBarrier()) {
      context.writeBarrier(object, referenceSlot, value, null, null, Plan.AASTORE_WRITE_BARRIER);
      if (gcEveryWB) {
        gc();
      }
    } else {
      referenceSlot.store(value);
    }
  }

  /**
   * Load and return the value of a data field of an object.
   *
   * @param object The object to load the field of.
   * @param index The field index.
   */
  public int loadDataField(ObjectReference object, int index) {
    int limit = ObjectModel.getDataCount(object);
    check(!object.isNull(), "Object can not be null");
    check(index >= 0, "Index must be non-negative");
    check(index < limit, "Index out of bounds");

    Address dataSlot = ObjectModel.getDataSlot(object, index);
    int result = dataSlot.loadInt();
    Trace.trace(Item.LOAD,"[%s].int[%d] returned [%d]",object.toString(),index,result);
    return result;
  }

  /**
   * Load and return the value of a reference field of an object.
   *
   * @param object The object to load the field of.
   * @param index The field index.
   */
  public ObjectReference loadReferenceField(ObjectReference object, int index) {
    int limit = ObjectModel.getRefs(object);
    check(!object.isNull(), "Object can not be null");
    check(index >= 0, "Index must be non-negative");
    check(index < limit, "Index out of bounds");

    Address referenceSlot = ObjectModel.getRefSlot(object, index);
    ObjectReference result;
    if (ActivePlan.constraints.needsReadBarrier()) {
      result = context.readBarrier(object, referenceSlot, null, null, Plan.AASTORE_WRITE_BARRIER);
    } else {
      result = referenceSlot.loadObjectReference();
    }
    Trace.trace(Item.LOAD,"[%s].object[%d] returned [%s]",object.toString(),index,result.toString());
    return result;
  }

  /**
   * Get the hash code for the given object.
   */
  public int hash(ObjectReference object) {
    check(!object.isNull(), "Object can not be null");
    int result = ObjectModel.getHashCode(object);
    Trace.trace(Item.HASH,"hash(%s) returned [%d]",object.toString(),result);
    return result;
  }

  /**
   * Allocate an object and return a reference to it.
   *
   * @param refCount The number of reference fields.
   * @param dataCount The number of data fields.
   * @param doubleAlign Is this an 8 byte aligned object?
   * @return The object reference.
   */
  public ObjectReference alloc(int refCount, int dataCount, boolean doubleAlign, int allocSite) {
    check(refCount >= 0, "Non-negative reference field count required");
    check(dataCount >= 0, "Non-negative data field count required");
    ObjectReference result = ObjectModel.allocateObject(context, refCount, dataCount, doubleAlign, allocSite);
    Trace.trace(Item.ALLOC,"alloc(" + refCount + ", " + dataCount + ", " + doubleAlign + ") returned [" + result + "]");
    return result;
  }

  /**
   * Return a string identifying the allocation site of an object
   * @param object
   * @return
   */
  public static String getSiteName(ObjectReference object) {
    return AllocationSite.getSite(ObjectModel.getSite(object)).toString();
  }
}
