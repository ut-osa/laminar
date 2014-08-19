package org.jikesrvm.scheduler;

import org.jikesrvm.VM;

/*Exception class for violations of invocation-number
 * in Airavat
 */
public class AiravatException extends SecurityException{


  public AiravatException(String msg) {
    super(msg);
    if (DIFC.verbosity >= 3) {
      VM.sysWrite("Creating a DIFC exception here:");
      Scheduler.dumpStack();
    }
  }
  /*Lets put the golden ratio. Is there a convention? */
  private static final long serialVersionUID = 16180339887L;
}
