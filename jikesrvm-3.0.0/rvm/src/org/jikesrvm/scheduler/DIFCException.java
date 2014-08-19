package org.jikesrvm.scheduler;

import org.jikesrvm.VM;

/* DIFC
 * This exception is thrown when ever the runtime detects
 * a difc security violation 
 */
public class DIFCException extends SecurityException{

  public DIFCException(String msg) {
    super(msg);
    // DIFC: TODO: change back to 2 or 3
    if (DIFC.verbosity >= 1) {
      VM.sysWrite("Creating a DIFC exception here:");
      Scheduler.dumpStack();
    }
  }
  /*Lets put the golden ratio. Is there a convention? */
  private static final long serialVersionUID = 16180339887L;
}
