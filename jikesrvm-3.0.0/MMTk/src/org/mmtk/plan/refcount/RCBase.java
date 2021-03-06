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
package org.mmtk.plan.refcount;

import org.mmtk.plan.StopTheWorld;
import org.mmtk.plan.Trace;
import org.mmtk.plan.refcount.cd.CD;
import org.mmtk.plan.refcount.cd.NullCD;
import org.mmtk.plan.refcount.cd.TrialDeletion;
import org.mmtk.policy.ExplicitFreeListSpace;
import org.mmtk.policy.ExplicitLargeObjectLocal;
import org.mmtk.policy.Space;
import org.mmtk.utility.deque.SharedDeque;
import org.mmtk.utility.heap.VMRequest;
import org.mmtk.utility.options.Options;
import org.mmtk.utility.sanitychecker.SanityChecker;
import org.mmtk.utility.statistics.EventCounter;

import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements the global state of a simple reference counting
 * collector.
 *
 * All plans make a clear distinction between <i>global</i> and
 * <i>thread-local</i> activities, and divides global and local state
 * into separate class hierarchies.  Global activities must be
 * synchronized, whereas no synchronization is required for
 * thread-local activities.  There is a single instance of Plan (or the
 * appropriate sub-class), and a 1:1 mapping of PlanLocal to "kernel
 * threads" (aka CPUs or in Jikes RVM, Processors).  Thus instance
 * methods of PlanLocal allow fast, unsychronized access to functions such as
 * allocation and collection.
 *
 * The global instance defines and manages static resources
 * (such as memory and virtual memory resources).  This mapping of threads to
 * instances is crucial to understanding the correctness and
 * performance properties of MMTk plans.
 */
@Uninterruptible public abstract class RCBase extends StopTheWorld {

  /****************************************************************************
   * Constants
   */
  public static final boolean WITH_COALESCING_RC         = true;
  public static final boolean INC_DEC_ROOT               = true;
  public static final boolean INLINE_WRITE_BARRIER       = WITH_COALESCING_RC;
  public static final boolean GATHER_WRITE_BARRIER_STATS = false;
  public static final boolean FORCE_FULL_CD              = false;

  // Cycle detection selection
  public static final int     NO_CYCLE_DETECTOR = 0;
  public static final int     TRIAL_DELETION    = 1;
  public static final int     CYCLE_DETECTOR    = TRIAL_DELETION;

  /****************************************************************************
   * Class variables
   */
  public static final ExplicitFreeListSpace rcSpace = new ExplicitFreeListSpace("rc", DEFAULT_POLL_FREQUENCY, VMRequest.create(0.5f));
  public static final int REF_COUNT = rcSpace.getDescriptor();
  public static final ExplicitFreeListSpace smallCodeSpace = new ExplicitFreeListSpace("rc-sm-code", DEFAULT_POLL_FREQUENCY, VMRequest.create());
  public static final int RC_SMALL_CODE = smallCodeSpace.getDescriptor();

  // Counters
  public static EventCounter wbFast;
  public static EventCounter wbSlow;

  // Allocators
  public static final int ALLOC_RC = StopTheWorld.ALLOCATORS;
  public static final int ALLOCATORS = ALLOC_RC + 1;

  // Cycle Detectors
  private NullCD nullCD;
  private TrialDeletion trialDeletionCD;

  /****************************************************************************
   * Instance variables
   */

  public final Trace rcTrace;
  public final SharedDeque decPool;
  public final SharedDeque modPool;
  public final SharedDeque newRootPool;
  public final SharedDeque oldRootPool;
  protected int previousMetaDataPages;

  /**
   * Constructor.
 */
  public RCBase() {
    if (!SCAN_BOOT_IMAGE)VM.assertions.fail("RC requires scan boot image");
    /* Change defaults */
    Options.noReferenceTypes.setDefaultValue(true);
    Options.noFinalizer.setDefaultValue(true);
    if (GATHER_WRITE_BARRIER_STATS) {
      wbFast = new EventCounter("wbFast");
      wbSlow = new EventCounter("wbSlow");
    }
    previousMetaDataPages = 0;
    rcTrace = new Trace(metaDataSpace);
    decPool = new SharedDeque("decPool",metaDataSpace, 1);
    modPool = new SharedDeque("modPool",metaDataSpace, 1);
    newRootPool = new SharedDeque("newRootPool",metaDataSpace, 1);
    oldRootPool = new SharedDeque("oldRootPool",metaDataSpace, 1);
    switch (RCBase.CYCLE_DETECTOR) {
    case RCBase.NO_CYCLE_DETECTOR:
      nullCD = new NullCD();
      break;
    case RCBase.TRIAL_DELETION:
      trialDeletionCD = new TrialDeletion(this);
      break;
    }
  }

  /**
   * Perform any required initialization of the GC portion of the header.
   * Called for objects created at boot time.
   *
   * @param ref the object ref to the storage to be initialized
   * @param typeRef the type reference for the instance being created
   * @param size the number of bytes allocated by the GC system for
   * this object.
   * @param status the initial value of the status word
   * @return The new value of the status word
   */
  @Inline
  public Word setBootTimeGCBits(Address ref, ObjectReference typeRef,
                                int size, Word status) {
    if (WITH_COALESCING_RC) {
      status = status.or(SCAN_BOOT_IMAGE ? RCHeader.LOGGED : RCHeader.UNLOGGED);
    }
    return status;
  }

  /*****************************************************************************
   *
   * Collection
   */

  /**
   * Perform a (global) collection phase.
   *
   * @param phaseId Collection phase to execute.
   */
  @Inline
  public void collectionPhase(short phaseId) {
    if (phaseId == PREPARE) {
      rcTrace.prepare();
      modPool.prepareNonBlocking();
      decPool.prepare();
      oldRootPool.prepare();
      newRootPool.prepare();
      return;
    }

    if (phaseId == CLOSURE) {
      rcTrace.prepare();
      return;
    }

    if (phaseId == ROOTS) {
      oldRootPool.reset();
      super.collectionPhase(phaseId);
      return;
    }

    if (phaseId == RELEASE) {
      newRootPool.reset();
      decPool.reset();
      modPool.reset();

      rcTrace.release();
      previousMetaDataPages = metaDataSpace.reservedPages();
      return;
    }

    if (!cycleDetector().collectionPhase(phaseId)) {
      super.collectionPhase(phaseId);
    }
  }

  /**
   * This method controls the triggering of a GC. It is called periodically
   * during allocation. Returns true to trigger a collection.
   *
   * @param spaceFull Space request failed, must recover pages within 'space'.
   * @return True if a collection is requested by the plan.
   */
  public boolean collectionRequired(boolean spaceFull) {
    int newMetaDataPages = metaDataSpace.committedPages() - previousMetaDataPages;

    return super.collectionRequired(spaceFull) || (newMetaDataPages > Options.metaDataLimit.getPages());
  }

  /*****************************************************************************
   *
   * Accounting
   */

  /**
   * Return the number of pages reserved for use given the pending
   * allocation.  The superclass accounts for its spaces, we just
   * augment this with the mark-sweep space's contribution.
   *
   * @return The number of pages reserved given the pending
   * allocation, excluding space reserved for copying.
   */
  public int getPagesUsed() {
    return (rcSpace.reservedPages() + super.getPagesUsed());
  }

  /**
   * Calculate the number of pages a collection is required to free to satisfy
   * outstanding allocation requests.
   *
   * @return the number of pages a collection is required to free to satisfy
   * outstanding allocation requests.
   */
  public int getPagesRequired() {
    return super.getPagesRequired() + rcSpace.requiredPages();
  }

  /****************************************************************************
   *
   * Miscellaneous
   */

  /**
   * Determine if an object is in a reference counted space.
   *
   * @param object The object to check
   * @return True if the object is in a reference counted space.
   */
  public static boolean isRCObject(ObjectReference object) {
    return !object.isNull() && !Space.isInSpace(VM_SPACE, object);
  }

  /**
   * Free a reference counted object.
   *
   * @param object The object to free.
   */
  public static void free(ObjectReference object) {
    if (VM.VERIFY_ASSERTIONS) {
      VM.assertions._assert(isRCObject(object));
    }

    if (Space.isInSpace(REF_COUNT, object)) {
      ExplicitFreeListSpace.free(object);
    } else if (Space.isInSpace(LOS, object)){
      ExplicitLargeObjectLocal.free(loSpace, object);
    } else if (Space.isInSpace(RC_SMALL_CODE, object)) {
      ExplicitFreeListSpace.free(object);
    } else if (Space.isInSpace(LARGE_CODE, object)) {
      ExplicitLargeObjectLocal.free(largeCodeSpace, object);
    }
  }

  /** @return The active cycle detector instance */
  @Inline
  public final CD cycleDetector() {
    switch (RCBase.CYCLE_DETECTOR) {
    case RCBase.NO_CYCLE_DETECTOR:
      return nullCD;
    case RCBase.TRIAL_DELETION:
      return trialDeletionCD;
    }

    VM.assertions.fail("No cycle detector instance found.");
    return null;
  }

  /**
   * @see org.mmtk.plan.Plan#willNeverMove
   *
   * @param object Object in question
   * @return True if the object will never move
   */
  @Override
  public boolean willNeverMove(ObjectReference object) {
    if (Space.isInSpace(REF_COUNT, object))
      return true;
    else if (Space.isInSpace(RC_SMALL_CODE, object))
      return true;
    return super.willNeverMove(object);
  }

  /**
   * Return the expected reference count. For non-reference counting
   * collectors this becomes a true/false relationship.
   *
   * @param object The object to check.
   * @param sanityRootRC The number of root references to the object.
   * @return The expected (root excluded) reference count.
   */
  public int sanityExpectedRC(ObjectReference object, int sanityRootRC) {
    if (RCBase.isRCObject(object)) {
      if (!RCHeader.isLiveRC(object)) {
        return SanityChecker.DEAD;
      }
      return RCHeader.getRC(object) - sanityRootRC;
    } else {
      return SanityChecker.UNSURE;
    }
  }
}
