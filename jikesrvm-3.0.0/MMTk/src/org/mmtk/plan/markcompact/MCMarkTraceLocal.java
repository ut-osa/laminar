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
package org.mmtk.plan.markcompact;

import org.mmtk.plan.TraceLocal;
import org.mmtk.plan.Trace;
import org.mmtk.policy.MarkCompactSpace;
import org.mmtk.policy.Space;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implments the thread-local functionality for a transitive
 * closure over a mark-compact space during the initial marking phase.
 */
@Uninterruptible
public final class MCMarkTraceLocal extends TraceLocal {
  /**
   * Constructor
   */
  public MCMarkTraceLocal(Trace trace) {
    super(MC.SCAN_MARK, trace);
  }

  /****************************************************************************
   *
   * Externally visible Object processing and tracing
   */

  /**
   * Is the specified object live?
   *
   * @param object The object.
   * @return True if the object is live.
   */
  public boolean isLive(ObjectReference object) {
    if (object.isNull()) return false;
    if (Space.isInSpace(MC.MARK_COMPACT, object)) {
      return MC.mcSpace.isLive(object);
    }
    return super.isLive(object);
  }

  /**
   * This method is the core method during the trace of the object graph.
   * The role of this method is to:
   *
   * 1. Ensure the traced object is not collected.
   * 2. If this is the first visit to the object enqueue it to be scanned.
   * 3. Return the forwarded reference to the object.
   *
   * In this instance, we refer objects in the mark-sweep space to the
   * msSpace for tracing, and defer to the superclass for all others.
   *
   * @param object The object to be traced.
   * @return The new reference to the same object instance.
   */
  @Inline
  public ObjectReference traceObject(ObjectReference object) {
    if (object.isNull()) return object;
    if (Space.isInSpace(MC.MARK_COMPACT, object))
      return MC.mcSpace.traceMarkObject(this, object);
    return super.traceObject(object);
  }

  /**
   * Ensure that this object will not move for the rest of the GC.
   *
   * @param object The object that must not move
   * @return The new object, guaranteed stable for the rest of the GC.
   */
  @Inline
  public ObjectReference precopyObject(ObjectReference object) {
    if (Space.isInSpace(MC.MARK_COMPACT, object)) {
      if (MarkCompactSpace.testAndMark(object)) {
        // TODO: If precopy returns many different objects, this will cause a leak.
        // Currently, Jikes RVM does not require many objects to be precopied.
        ObjectReference newObject = VM.objectModel.copy(object, MC.ALLOC_IMMORTAL);
        MarkCompactSpace.setForwardingPointer(object, newObject);
        processNode(newObject);
        return newObject;
      }
      // Somebody else got to it first
      while (MarkCompactSpace.getForwardingPointer(object).isNull());
      return MarkCompactSpace.getForwardingPointer(object);
    }
    return super.precopyObject(object);
  }

  /**
   * Will this object move from this point on, during the current trace ?
   *
   * @param object The object to query.
   * @return True if the object will not move.
   */
  public boolean willNotMoveInCurrentCollection(ObjectReference object) {
    // All objects in the MC space may move
    return !Space.isInSpace(MC.MARK_COMPACT, object);
  }

}
