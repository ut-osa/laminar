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
package org.mmtk.harness.options;

import org.mmtk.harness.Harness;

/**
 * Number of collector threads.
 */
public final class Trace extends org.vmutil.options.EnumOption {
  /**
   * Create the option.
   */
  public Trace() {
    super(Harness.options, "Trace",
        "Harness debugging trace options",
        org.mmtk.harness.lang.Trace.itemNames(),
        "NONE");
  }

  public void apply() {
    switch(getValue()) {
      case 0:
        break;
      default: {
        org.mmtk.harness.lang.Trace.enable(values[getValue()]);
        break;
      }
    }
  }

  /**
   * Only accept non-negative values.
   */
  protected void validate() {
  }
}
