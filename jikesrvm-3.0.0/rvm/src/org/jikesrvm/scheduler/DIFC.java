/************************************
Operating Systems & Architecture Group
University of Texas at Austin - Department of Computer Sciences
Copyright 2009, 2010. All Rights Reserved.
See LICENSE file for license terms.

Authors(in alphabetical order):

Michael D. Bond
Kathryn S. Mckinley
Donald E. Porter
Indrajit Roy
Emmett Witchel
**************************************/

//DIFC: Interface between application and Jikes
//We also put other DIFC code here for convenience
package org.jikesrvm.scheduler;

import java.lang.reflect.Method;

import org.jikesrvm.Callbacks;
import org.jikesrvm.Constants;
import org.jikesrvm.VM;
import org.jikesrvm.Callbacks.ExitMonitor;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.FieldReference;
import org.jikesrvm.classloader.MemberReference;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.compilers.opt.inlining.InlineSequence;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.mm.mminterface.MemoryManager;
import org.jikesrvm.objectmodel.ObjectModel;
import org.jikesrvm.runtime.Entrypoints;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.RuntimeEntrypoints;
import org.jikesrvm.scheduler.greenthreads.GreenProcessor;
import org.jikesrvm.scheduler.greenthreads.GreenThread;
import org.jikesrvm.util.StringUtilities;
import org.mmtk.plan.Plan;
import org.vmmagic.pragma.Entrypoint;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.NoBoundsCheck;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.pragma.NoNullCheck;
import org.vmmagic.pragma.NoSideEffects;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.UninterruptibleNoWarn;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;

import static org.jikesrvm.runtime.SysCall.sysCall;

public final class DIFC {

  public static final boolean enabled = VM.difcEnabled;
  public static final int verbosity = (VM.difcVerbose ? 2 : 1);
  /*Used to specify the type of capability*/
  public static final int PLUS_CAPABILITY=0, MINUS_CAPABILITY=1, BOTH_CAPABILITY=2;
  /*Used to specify the set where the capability should be added*/
  public static final int REGION_SELF=3, REGION_GROUP=4, REGION_NONE=5;
  /*Label type used in object-label map*/
  public static final int SECRECY=0, INTEGRITY=1;
  /*The different sets*/
  public static final int LABELS=0, CAPABILITY=1;
  /*Drop capabilities permanent or temporarily*/
  public static final int DROP_PERMANENT=0, DROP_TEMPORARY=1;
  /*Lets us know when boot is complete*/
  private static boolean BOOT_COMPLETE=false;
  /* Value of cycles when we start */
  private static long startCycles;
  public static long totalCyclesInSecureRegions;
  private static int secureRegionCount;

  /*Airavat: added*/ 
  public static final boolean isAiravat= VM.airavatEnabled;
  public static final long AIRAVAT_CONFIG = -10;
  // called when the VM finishes booting (right before starting main thread)
  public static void init() {
    if (enabled) {
      GreenThread.commonPlusCapabilitySet=LabelSet.EMPTY;
      GreenThread.commonMinusCapabilitySet=LabelSet.EMPTY;
      BOOT_COMPLETE=true;

      /*
      if (LabelSet.PROFILE) {
        Callbacks.addExitMonitor(new LabelSet.ProfileCallback());
      }
      */

      // profile time in secure regions
      
      Callbacks.addExitMonitor(new Callbacks.ExitMonitor() {
        public void notifyExit(int value) {
          if(!isAiravat){
            long totalCycles = Magic.getTimeBase() - startCycles;
            System.out.println("totalCycles = " + totalCycles);
            System.out.println("cyclesInSecureRegions = " + totalCyclesInSecureRegions);
            double frac = (double)totalCyclesInSecureRegions / totalCycles;
            System.out.println("Fraction time in SRs = " + frac);
            System.out.println("Number of SRs = " + secureRegionCount);
            System.out.println("Number of new LabelSets = " + LabelSet.newLabelSets);
          }
        }
      });
          
      startCycles = Magic.getTimeBase();

    }
  }

    public static void resetStartCycles() {
	startCycles = Magic.getTimeBase();
	totalCyclesInSecureRegions = 0;
	secureRegionCount = 0;
    }

  /*Called to mark the start of a secure region*/
  // DIFC: TODO: trying inlining this since there should be a lot of opportunities
  @Inline @UninterruptibleNoWarn
  //@NoNullCheck @NoBoundsCheck @NoSideEffects
  public static void startSecureRegion(
      LabelSet secrecySet,
      LabelSet integritySet) throws DIFCException{

    //profile("DIFC.startSecureRegion");
    
    // DIFC: TODO: uncomment!
    Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).startSecureRegionCycles = Magic.getTimeBase();

    /* Check the Thread has the right privileges to give power to the secure region
     * Threads currently have no persistent labels, rather only capabilities.
     * Secrecy and integrity labels should be in plusCapability of the current thread
     * or in that of list of capabilities common to all threads
     * TODO: check that the capabilities are also a subset
     * */
    
    if(secrecySet==null) secrecySet=LabelSet.EMPTY;
    if(integritySet==null) integritySet=LabelSet.EMPTY;
    startSecureRegionHelper(LabelSet.EMPTY, LabelSet.EMPTY, secrecySet, integritySet);
  }

  @Inline @UninterruptibleNoWarn
  //@NoNullCheck @NoBoundsCheck @NoSideEffects
  public static void startSecureRegionDecEnd(
      LabelSet secrecySet,
      LabelSet integritySet,
      LabelSet plusCapabilitySet,
      LabelSet minusCapabilitySet) throws DIFCException{

    //profile("DIFC.startSecureRegion");
    
    // DIFC: TODO: uncomment!
    Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).startSecureRegionCycles = Magic.getTimeBase();

    /* Check the Thread has the right privileges to give power to the secure region
     * Threads currently have no persistent labels, rather only capabilities.
     * Secrecy and integrity labels should be in plusCapability of the current thread
     * or in that of list of capabilities common to all threads
     * TODO: check that the capabilities are also a subset
     * */
    
    if(secrecySet==null) secrecySet=LabelSet.EMPTY;
    if(integritySet==null) integritySet=LabelSet.EMPTY;
    if(plusCapabilitySet==null) plusCapabilitySet=LabelSet.EMPTY;
    if(minusCapabilitySet==null) minusCapabilitySet=LabelSet.EMPTY;
    startSecureRegionHelper(plusCapabilitySet, minusCapabilitySet, secrecySet, integritySet);
  }

    
  
  @UninterruptibleNoWarn
  public static void startSecureRegionHelper(LabelSet plusCapabilitySet,
      LabelSet minusCapabilitySet,
      LabelSet secrecySet,
      LabelSet integritySet) {
    GreenProcessor pr = Magic.processorAsGreenProcessor(Processor.getCurrentProcessor());
    GreenThread current = (GreenThread) pr.getCurrentThread();
    SRState curSRState=current.currentSRState;
    //We are starting a SR, is this is the first SR then it means that the secrecy and integrity sets of the parent (i.e. thread) is empty
    LabelSet plusCap=curSRState.plusCapabilitySet;
    LabelSet minusCap=curSRState.minusCapabilitySet;
    if(!current.inSecureRegion){
      //We need to account for the global capabilities
      plusCap=LabelSet.union(plusCap, GreenThread.commonPlusCapabilitySet);
      minusCap=LabelSet.union(minusCap, GreenThread.commonMinusCapabilitySet);
    }
    
    if(!secrecySet.checkInUnion(plusCap,curSRState.secrecySet))
      throwStartError("Parent does not have the capability or label to add the secrecy label");
    if(!curSRState.secrecySet.checkInUnion(minusCap,secrecySet))
      throwStartError("Parent does not have the capability to remove the secrecy label");
    if(!integritySet.checkInUnion(plusCap,curSRState.integritySet))
      throwStartError("Parent does not have the capability or label to add the integrity label");
    if(!curSRState.integritySet.checkInUnion(minusCap,integritySet))
      throwStartError("Parent does not have the capability to remove the integrity label");
    if(!plusCapabilitySet.isSubsetOf(plusCap))
      throwStartError("Parent does not have the capability to add the plus capability");
    if(!minusCapabilitySet.isSubsetOf(minusCap))
      throwStartError("Parent does not have the capability to add the minus capability");

    //if (error!=null) means the parent does not have the right permission to start the child SR
    //lets add a dummy value to the SR stack so that when the user calls endSecureRegion in
    //his/her try-catch block , we don't  go haywire.
      
    SRState child = curSRState.getChildState();
    if(child!=null){
      //lets reuse the child state
      // We set the allocation labels to null, which means newly allocated objects are labeled
      child.setState(plusCapabilitySet, minusCapabilitySet, secrecySet, integritySet, false, null,null);
    }else{
      child=new SRState(plusCapabilitySet, minusCapabilitySet, secrecySet, integritySet, false, null,null);
      curSRState.child = child;
      child.setParentState(curSRState);
    }
    
    current.currentSRState = child;
    current.inSecureRegion = true;
    
    if (verbosity >= 2) {
      VM.sysWriteln("Debug: start");
    }
    // DIFC: TODO: uncomment
    secureRegionCount++;
  }

  // error path
  private static final void throwStartError(String error) {
    //an error had occurred, so lets give the dummy child empty labels
    SRState child;
    GreenProcessor pr = Magic.processorAsGreenProcessor(Processor.getCurrentProcessor());
    GreenThread current = (GreenThread) pr.getCurrentThread();
    SRState curSRState = current.currentSRState;
    child = curSRState.getChildState();
    if (child != null) {
      child.clearState();
    } else {
      child = new SRState();
      curSRState.setChildState(child);
      child.setParentState(curSRState);
    }
    current.currentSRState = child;
    throw new DIFCException(error);
  }
  
  /*Called to mark end of secure region*/
  @UninterruptibleNoWarn @Inline
  // DIFC: TODO: doesn't work? @NoNullCheck @NoBoundsCheck @NoSideEffects
  public static void endSecureRegion() {
    
    // DIFC: TODO: uncomment!
    // long elapsed = Magic.getTimeBase() - Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).startSecureRegionCycles;
    // DIFC.totalCyclesInSecureRegions += elapsed;
    GreenProcessor pr = Magic.processorAsGreenProcessor(Processor.getCurrentProcessor());
    GreenThread current = (GreenThread) pr.getCurrentThread();
    if(!current.inSecureRegion) return;
    //profile("DIFC.endSecureRegion");
  
    /*Lets just pop the stack*/
    SRState state=current.currentSRState;
    if(state.shouldRestoreLabel()){
      /*TODO: use the TCB replace call. This is currently broken due to the multiplexing of user threads on jikes threads. 
       * It would be easier to handle in the next version of jikes where user threads have one-to-one mapping with kernel threads.*/
      sysCall.sysPassLabels(null, 0, null, 0);
      //sysCall.sysReplaceLabelsTCB(state.parent.secrecySet.getLongLabels(),state.parent.secrecySet.len,state.parent.integritySet.getLongLabels(),state.parent.integritySet.len);
    }
    SRState parent = state.getParentState();
    current.currentSRState=parent;
    //check if we are out of all SR's
    if(parent.getParentState()==null)
      current.inSecureRegion = false;
    //If we are outside a SR then 
    // allocation labels=EMPTY,which means to allocate unlabeled objects
    // (which should happen anyway since a simple unlabeled allocation should
    //  be compiled outside SRs)
    if (verbosity >= 2) {
      VM.sysWriteln("Debug: end");
    }
  }

  /*Called to add secrecy label. Will fail if capability does not exist*/
  public static void addSecrecyLabel(LabelSet label) throws DIFCException{
    
    //profile("DIFC.addSecrecyLabel");

    /*Only secure regions can add labels*/
    if(label==null) return;
    if(((GreenThread) Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).getCurrentThread()).inSecureRegion){
	SRState curState=((GreenThread) Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).getCurrentThread()).currentSRState;
      if(!label.isSubsetOf(curState.plusCapabilitySet))
          throw new DIFCException("Lacks capability to add secrecy label");
      curState.secrecySet=LabelSet.union(curState.secrecySet,label);
    }else
      throw new DIFCException("Cannot add secrecy label outside SR");
  } 

  /*Called to add integrity label. Will fail if capability does not exist*/
  public static void addIntegrityLabel(LabelSet label) throws DIFCException{

    //profile("DIFC.addIntegrityLabel");

    /*Only secure regions can add labels*/
    if(label==null) return;
    if(((GreenThread) Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).getCurrentThread()).inSecureRegion){
      SRState curState=((GreenThread) Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).getCurrentThread()).currentSRState;
        if(!label.isSubsetOf(curState.plusCapabilitySet))
          throw new DIFCException("Lacks capability to add integrity label");
        curState.integritySet=LabelSet.union(curState.integritySet,label);
    }else
      throw new DIFCException("Cannot add integrity label outside SR");
  }

  /*Called to remove secrecy label. Will fail if capability does not exist*/
  public static void removeSecrecyLabel(LabelSet label) throws DIFCException{

    //profile("DIFC.removeSecrecyLabel");

    /*Only secure regions can remove labels*/
    if(((GreenThread) Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).getCurrentThread()).inSecureRegion){
      SRState curState=((GreenThread) Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).getCurrentThread()).currentSRState;
      if(label.isSubsetOf(curState.minusCapabilitySet)){
        curState.secrecySet=LabelSet.minus(curState.secrecySet,label);
        return;
      }
    }
    throw new DIFCException("Cannot remove secrecy label");
  }

  /*Called to remove integrity label. Will fail if capability does not exist*/
  public static void removeIntegrityLabel(LabelSet label) throws DIFCException{

    //profile("DIFC.removeIntegrityLabel");

    /*Only secure regions can remove labels*/
    if(((GreenThread) Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).getCurrentThread()).inSecureRegion){
      SRState curState=((GreenThread) Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).getCurrentThread()).currentSRState;
      if(label.isSubsetOf(curState.minusCapabilitySet)){
        curState.integritySet=LabelSet.minus(curState.integritySet,label);
        return;
      }
    }
    throw new DIFCException("Cannot remove integrity label");
  }

  /* This function drops the capability of the current security scope. Inside
   * a secure region it will drop the secure regions capability. If outside a secure
   * region it will drop the thread's capability.
   * */
  public static void dropCapability(LabelSet label, int type) throws DIFCException{

    //profile("DIFC.dropCapability");

    if(!(type==PLUS_CAPABILITY||type==MINUS_CAPABILITY||type==BOTH_CAPABILITY))
      throw new DIFCException("Wrong type of capability");
    SRState curState=((GreenThread) Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).getCurrentThread()).currentSRState;
    if(type==PLUS_CAPABILITY || type==BOTH_CAPABILITY) 
      curState.plusCapabilitySet=LabelSet.minus(curState.plusCapabilitySet, label);
    if(type==MINUS_CAPABILITY || type==BOTH_CAPABILITY)
      curState.minusCapabilitySet=LabelSet.minus(curState.minusCapabilitySet, label);
    //TODO: OSdropCapability(label, type, ?);
  }

  /* This function drops the capability of the Thread. If called inside a secure
   * region it will drop the capability of both the thread and the current secure region.
   * */
  public static void dropThreadCapability(LabelSet label, int type) throws DIFCException{

    SRState curState=((GreenThread) Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).getCurrentThread()).currentSRState;

    /* DP: This is a write to the capability set.  Only allow if label is empty.*/
    if (curState.secrecySet != LabelSet.EMPTY)
	throw new DIFCException("Attempt to write capability set in secret SR.");

    //profile("DIFC.dropThreadCapability");
    //TODO: fix this for nested SR
    dropCapability(label, type);
    /*Lets remove the threads capability if this was called inside the secure region*/
    if(((GreenThread) Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).getCurrentThread()).inSecureRegion){
      if(type==PLUS_CAPABILITY || type==BOTH_CAPABILITY) 
        curState.parent.plusCapabilitySet = LabelSet.minus(curState.parent.plusCapabilitySet, label);
      if(type==MINUS_CAPABILITY || type==BOTH_CAPABILITY)
        curState.parent.minusCapabilitySet = LabelSet.minus(curState.parent.minusCapabilitySet, label);
    }
    //TODO: make syscall to drop capability from the OS 
  }

  /* Allocates a new capability. It adds the capability (type=plus, minus or both)
   * to the thread or shared thread capability set (specified by region) 
   * The capability is always added to the current security scope. E.g. if called
   * inside a secure-region the capability is added to the secure-region cap-set (since
   * it is the owner) and to set specified by region. 
   * TODO: do we need synchronized if the OS is creating labels?
   */
  public static synchronized long createCapability(int type, int region){

    //profile("DIFC.createCapability");
    SRState curState=((GreenThread) Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).getCurrentThread()).currentSRState;
    /* DP: This is a write to the capability set.  Only allow if label is empty.*/
    if (curState.secrecySet != LabelSet.EMPTY)
	throw new DIFCException("Attempt to write capability set in secret SR.");

    if(!(type==PLUS_CAPABILITY||type==MINUS_CAPABILITY||type==BOTH_CAPABILITY))
      throw new DIFCException("Wrong type of capability");
    long label=OScreateLabel(type,region);

    if(label<=0)
	throw new DIFCException("OS capability creation failed: " + label);
    
    boolean inSecureRegion = ((GreenThread) GreenProcessor.getCurrentThread()).inSecureRegion;
    addThreadCapability(type,label, true);
    if(inSecureRegion && region==REGION_SELF)
      addThreadCapability(type,label, false);
    if(region==REGION_GROUP){
      if(type==PLUS_CAPABILITY || type==BOTH_CAPABILITY) 
        GreenThread.commonPlusCapabilitySet=LabelSet.union(GreenThread.commonPlusCapabilitySet,label);
      if(type==MINUS_CAPABILITY || type==BOTH_CAPABILITY)
        GreenThread.commonMinusCapabilitySet=LabelSet.union(GreenThread.commonMinusCapabilitySet,label);
    }
    return label;
  }

  /*Helper function to add capability to the thread or the secure-region*/
  private static void addThreadCapability(int type, long label, boolean current) {
    //TODO: fix the nested SR region
    //profile("DIFC.addThreadCapability");

    SRState curState=((GreenThread) Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).getCurrentThread()).currentSRState;
    if (type==PLUS_CAPABILITY || type==BOTH_CAPABILITY) {
      if (current) {
        curState.plusCapabilitySet = LabelSet.union(curState.plusCapabilitySet, label);
      } else {
        curState.parent.plusCapabilitySet = LabelSet.union(curState.parent.plusCapabilitySet, label);
      }
    }
    if (type==MINUS_CAPABILITY || type==BOTH_CAPABILITY) {
      if (current) {
        curState.minusCapabilitySet = LabelSet.union(curState.minusCapabilitySet, label);
      } else {
        curState.parent.minusCapabilitySet = LabelSet.union(curState.parent.minusCapabilitySet, label);
      }
    }
  }

  /*Function to get the labels or capabilities of the current scope
   * set= capability or label or shared_thread_cap
   * type = plus/minus capability or secrecy/integrity 
   * If called inside a secure region it will return the capabilities of the
   * secure region otherwise those of the thread
   * */
  public static LabelSet getProperty(int set, int type){
    
    //profile("DIFC.getProperty");
    
    if(set==LABELS){
      /*Only secure regions have labels associated with them*/
      if(!((GreenThread) Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).getCurrentThread()).inSecureRegion)
        return null;
      SRState curState=((GreenThread) Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).getCurrentThread()).currentSRState;
      switch(type){
      case SECRECY: return curState.secrecySet;
      case INTEGRITY: return curState.integritySet;
      default: return null;
      }
    }else if(set==CAPABILITY){
      SRState curState=((GreenThread) Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).getCurrentThread()).currentSRState;
      switch(type){
      case PLUS_CAPABILITY: return curState.plusCapabilitySet;
      case MINUS_CAPABILITY: return curState.minusCapabilitySet;
      default: return null;
      }
    }else if(set==REGION_GROUP){
      switch(type){
      case PLUS_CAPABILITY: return GreenThread.commonPlusCapabilitySet;
      case MINUS_CAPABILITY: return GreenThread.commonMinusCapabilitySet;
      default: return null;
      }
    }
    return null;
  }

  /*Check if the flow of information from the source to the destination is allowed*/
  @Uninterruptible
  @Inline
  @NoNullCheck @NoBoundsCheck @NoSideEffects
  private static boolean checkAllowed(LabelSet srcSecSet,
      LabelSet srcIntSet,
      LabelSet destSecSet,
      LabelSet destIntSet){

    //profile("DIFC.checkAllowed");

    /*secrecy subset relation*/
      if(!srcSecSet.isSubsetOf(destSecSet))	
        return false;
    /*integrity subset relation*/
      if(!destIntSet.isSubsetOf(srcIntSet))	
        return false;
    return true;
  }

  // Read barrier: gets executed before every object field or array slot read
  
  @Entrypoint @Uninterruptible
  public static final void readBarrierDynamicDebug(Object obj) {
    if(((GreenThread) Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).getCurrentThread()).inSecureRegion){
      readBarrierInsideSRDebug(obj);
    } else {
      readBarrierOutsideSRDebug(obj);
    }
  }

  @Entrypoint @Uninterruptible
  public static final void airavatReadBarrierDynamicDebug(Object obj) {
    if (Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).inMapperRegion) {
      airavatReadBarrierInsideSRDebug(obj);
    } else {
      airavatReadBarrierOutsideSRDebug(obj);
    }
  }

  @Entrypoint @Inline @Uninterruptible
  @NoNullCheck @NoBoundsCheck @NoSideEffects
  public static final void readBarrierDynamic(Object obj) {
    if(((GreenThread) Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).getCurrentThread()).inSecureRegion){
      readBarrierInsideSR(obj);
    } else {
      readBarrierOutsideSR(obj);
    }
  }

  @Entrypoint @Inline @Uninterruptible
  @NoNullCheck @NoBoundsCheck @NoSideEffects
  public static final void airavatReadBarrierDynamic(Object obj) {
    if (Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).inMapperRegion) {
      airavatReadBarrierInsideSR(obj);
    }
  }

  @Entrypoint @Uninterruptible
  public static final void readBarrierOutsideSRDebug(Object obj) {
    //profile("DIFC.readBarrierOutsideSR");
       if (VM.VerifyAssertions) { VM._assert(!((GreenThread) Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).getCurrentThread()).inSecureRegion); }
    readBarrierOutsideSR(obj);
  }

  @Entrypoint @Uninterruptible
  public static final void airavatReadBarrierOutsideSRDebug(Object obj) {
    if (VM.VerifyAssertions) { VM._assert(!Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).inMapperRegion); }
  }

  @Entrypoint  @Inline  @UninterruptibleNoWarn
  @NoNullCheck @NoBoundsCheck @NoSideEffects
  public static final void readBarrierOutsideSR(Object obj) {
    /*Laminar code*/
    final Word value = ObjectReference.fromObject(obj).toAddress().toWord();
    final Word unsignedOffset = value.minus(MemoryManager.labeledStart());
    if (unsignedOffset.LT(MemoryManager.labeledExtent())) {
      throw new DIFCException("Labeled/Poisoned object accessed outside secure region");
    }
  }

  @Entrypoint @Uninterruptible
  public static final void readBarrierInsideSRDebug(Object obj) {
    //profile("DIFC.readBarrierInsideSR");
    if (VM.VerifyAssertions) { VM._assert(((GreenThread) Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).getCurrentThread()).inSecureRegion); }
    readBarrierInsideSR(obj);
  }
  
  @Entrypoint @Uninterruptible
  public static final void airavatReadBarrierInsideSRDebug(Object obj) {
    if (VM.VerifyAssertions) { VM._assert(Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).inMapperRegion); }
    airavatReadBarrierInsideSR(obj);
  }
  
  @Entrypoint @UninterruptibleNoWarn
  @NoNullCheck @NoBoundsCheck @NoSideEffects
  public static final void readBarrierInsideSR(Object obj) {
    if (!VM.difcNoRWBarrierContents) {
      // optimized for common, cheaper case
      if (!MemoryManager.isLabeled(obj)) {
        /** The object has no secrecy or integrity label. We are allowed to read it unless the SR
          has some integrity label attached to it */
        SRState curState=((GreenThread) Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).getCurrentThread()).currentSRState;
        if(curState.integritySet != LabelSet.EMPTY)
          throw new DIFCException("Read access violation: SR with non-null integrity attempting to read unlabeled data");
      } else {
        if (!VM.difcNoSlowPath) {
          readBarrierSlowPath(obj);
        }
      }
    }
  }

  @Entrypoint @UninterruptibleNoWarn
  @NoNullCheck @NoBoundsCheck @NoSideEffects
  public static void airavatReadBarrierInsideSR(Object obj) {
    if(!MemoryManager.isLabeled(obj))
      throwAiravatException("Reading unlabeled object inside mapper");
    else{
      checkInvocationReadRule(getSecrecyLabels(obj));
    }
  }

  @UninterruptibleNoWarn @Inline
  @NoNullCheck @NoBoundsCheck @NoSideEffects
  private static final void readBarrierSlowPath(Object obj) {
    //if (VM.VerifyAssertions) { VM._assert(isLabeled(obj)); }
    /*
    if (verbosity >= 3) {
      VM.sysWrite("In SR in read barrier: ");
      VM.sysWrite(ObjectReference.fromObject(obj));
      VM.sysWriteln();
      VM.sysWriteln("label="+isLabeled(obj));
    }*/
    LabelSet secrecyLabels = getSecrecyLabels(obj);
    LabelSet integrityLabels = getIntegrityLabels(obj);
    /*Check if we have the right permission to read*/
    if (VM.difcNoSubsetChecks) {
      // do this dummy check to make sure that the optimizer doesn't dead-code-eliminate the loads
      Word secWord = ObjectReference.fromObject(secrecyLabels).toAddress().toWord();
      Word intWord = ObjectReference.fromObject(integrityLabels).toAddress().toWord();
      if (secWord.or(intWord).isZero()) {
        throw new DIFCException("Shouldn't happen");
      }
    } else {
      SRState curState=((GreenThread) Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).getCurrentThread()).currentSRState;
      if(!checkAllowed(secrecyLabels,integrityLabels,curState.secrecySet,curState.integritySet))
    	  throw new DIFCException("Read access violation");
    }
  }

  // Write barrier: gets executed before every object field or array slot barrier

  @Entrypoint @Uninterruptible
  public static final void writeBarrierDynamicDebug(Object obj) {
    if(((GreenThread) Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).getCurrentThread()).inSecureRegion){
      writeBarrierInsideSRDebug(obj);
    } else {
      writeBarrierOutsideSRDebug(obj);
    }
  }

  @Entrypoint @Uninterruptible
  public static final void airavatWriteBarrierDynamicDebug(Object obj) {
    if (Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).inMapperRegion) {
      airavatWriteBarrierInsideSRDebug(obj);
    } else {
      airavatWriteBarrierOutsideSRDebug(obj);
    }
  }

  @Entrypoint @Inline @Uninterruptible
  @NoNullCheck @NoBoundsCheck @NoSideEffects
  public static final void writeBarrierDynamic(Object obj) {
    if(((GreenThread) Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).getCurrentThread()).inSecureRegion){
      writeBarrierInsideSR(obj);
    } else {
      writeBarrierOutsideSR(obj);
    }
  }

  @Entrypoint @Inline @Uninterruptible
  @NoNullCheck @NoBoundsCheck @NoSideEffects
  public static final void airavatWriteBarrierDynamic(Object obj) {
    if (Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).inMapperRegion) {
      airavatWriteBarrierInsideSR(obj);
    }
  }

  @Entrypoint @Uninterruptible
  public static final void writeBarrierOutsideSRDebug(Object obj) {
    //profile("DIFC.readBarrierOutsideSR");
    if (VM.VerifyAssertions) { VM._assert(!((GreenThread) Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).getCurrentThread()).inSecureRegion); }
    writeBarrierOutsideSR(obj);
  }

  @Entrypoint @Uninterruptible
  public static final void airavatWriteBarrierOutsideSRDebug(Object obj) {
    if (VM.VerifyAssertions) { VM._assert(!Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).inMapperRegion); }
  }

  @Entrypoint @Inline @UninterruptibleNoWarn
  @NoNullCheck @NoBoundsCheck @NoSideEffects
  public static final void writeBarrierOutsideSR(Object obj) {
    /*Laminar code*/
    final Word value = ObjectReference.fromObject(obj).toAddress().toWord();
    final Word unsignedOffset = value.minus(MemoryManager.labeledStart());
    if (unsignedOffset.LT(MemoryManager.labeledExtent())) {
      throw new DIFCException("Labeled/Poisoned object accessed outside secure region");
    }
  }

  @Entrypoint @Uninterruptible
  public static final void writeBarrierInsideSRDebug(Object obj) {
    //profile("DIFC.readBarrierInsideSR");
    if (VM.VerifyAssertions) { VM._assert(((GreenThread) Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).getCurrentThread()).inSecureRegion); }
    writeBarrierInsideSR(obj);
  }

  @Entrypoint @Uninterruptible
  public static final void airavatWriteBarrierInsideSRDebug(Object obj) {
    if (VM.VerifyAssertions) { VM._assert(Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).inMapperRegion); }
    airavatWriteBarrierInsideSR(obj);
  }

  @Entrypoint @UninterruptibleNoWarn
  @NoNullCheck @NoBoundsCheck @NoSideEffects
  public static final void writeBarrierInsideSR(Object obj) {
    if (!VM.difcNoRWBarrierContents) {
      // common fast case is inlined
      if (!MemoryManager.isLabeled(obj)) {
        /*We need to fail here unless:
         * 1) the SR's secrecy set is empty
         * TODO: 2) We have just de-classified an object or allocated an object and that object
         * is getting assigned in this write (this case is handled by allocation barrier?)*/
        //GreenThread currentThread = (GreenThread)GreenProcessor.getCurrentThread();
	SRState curState=((GreenThread) Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).getCurrentThread()).currentSRState;
        if(curState.secrecySet != LabelSet.EMPTY)
          throw new DIFCException("Write access violation: SR with non-null secrecy attempting to write unlabeled data");
      } else {
        if (!VM.difcNoSlowPath) {
          writeBarrierSlowPath(obj);
        }
      }
    }
  }

  @Entrypoint @UninterruptibleNoWarn
  @NoNullCheck @NoBoundsCheck @NoSideEffects
  public static final void airavatWriteBarrierInsideSR(Object obj) {
    GreenThread currentThread = (GreenThread)GreenProcessor.getCurrentThread();
    /*Airavat code*/
    //if(isAiravat){
      if (MemoryManager.isLabeled(obj)) {
        checkInvocationWriteRule(getSecrecyLabels(obj));
      }else{
        throwAiravatException("Writing to unlabeled object");
      }
    //}
  }

  @UninterruptibleNoWarn @Inline
  @NoNullCheck @NoBoundsCheck @NoSideEffects
  private static final void writeBarrierSlowPath(Object obj) {
    //if (VM.VerifyAssertions) { VM._assert(isLabeled(obj)); }
    /*
    if (verbosity >= 3) {
      VM.sysWrite("In SR in write barrier: ");
      VM.sysWrite(ObjectReference.fromObject(obj));
      VM.sysWriteln();
    }
    */
    /*Check if we have the right permission to write*/
    LabelSet secrecyLabels = getSecrecyLabels(obj);
    LabelSet integrityLabels = getIntegrityLabels(obj);
    if (VM.difcNoSubsetChecks) {
      // do this dummy check to make sure that the optimizer doesn't dead-code-eliminate the loads
      Word secWord = ObjectReference.fromObject(secrecyLabels).toAddress().toWord();
      Word intWord = ObjectReference.fromObject(integrityLabels).toAddress().toWord();
      if (secWord.or(intWord).isZero()) {
        throw new DIFCException("Shouldn't happen");
      }
    } else {
      SRState curState=((GreenThread) Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).getCurrentThread()).currentSRState;
      if(!checkAllowed(curState.secrecySet,curState.integritySet,secrecyLabels,integrityLabels))
        throw new DIFCException("Write access violation");
    }
  }
  
  @Entrypoint @Uninterruptible
  public static final void staticReadBarrierDynamicDebug(int fieldID) {
    if(((GreenThread) Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).getCurrentThread()).inSecureRegion){
      staticReadBarrierInsideSRDebug(fieldID);
    } else {
      staticReadBarrierOutsideSRDebug(fieldID);
    }
  }
  
  @Entrypoint @Uninterruptible
  public static final void airavatStaticReadBarrierDynamicDebug(int fieldID) {
    if (Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).inMapperRegion) {
      airavatStaticReadBarrierInsideSRDebug(fieldID);
    } else {
      airavatStaticReadBarrierOutsideSRDebug(fieldID);
    }
  }
  
  @Entrypoint @Inline @Uninterruptible
  public static final void staticReadBarrierDynamic(int fieldID) {
    if(((GreenThread) Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).getCurrentThread()).inSecureRegion){
      staticReadBarrierInsideSR(fieldID);
    } else {
      //staticReadBarrierOutsideSR(fieldID);
    }
  }
  
  @Entrypoint @Inline @Uninterruptible
  public static final void airavatStaticReadBarrierDynamic(int fieldID) {
    if (Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).inMapperRegion) {
      airavatStaticReadBarrierInsideSR(fieldID);
    } //else {
      //airavatStaticReadBarrierOutsideSR(fieldID);
    //}
  }
  
  @Entrypoint @Uninterruptible
  public static final void staticReadBarrierInsideSRDebug(int fieldID) {
    //profile("DIFC.staticReadBarrierInsideSR");
    staticReadBarrierInsideSR(fieldID);
  }

  @Entrypoint @Uninterruptible
  public static final void airavatStaticReadBarrierInsideSRDebug(int fieldID) {
    // do nothing: there is no non-debug version of this method
  }

  @Entrypoint @Inline @Uninterruptible
  public static final void staticReadBarrierInsideSR(int fieldID) {
    staticBarrierSlowPath(fieldID, false);
  }

  @Entrypoint @Inline @Uninterruptible
  public static final void airavatStaticReadBarrierInsideSR(int fieldID) {
    //staticBarrierSlowPath(fieldID, false);
    //Reads to statics are allowed since we do not allow writing to statics inside
    //mapper invocation. Thus statics cannot store per invocation information.
  }

  @Entrypoint @Uninterruptible
  public static final void staticReadBarrierOutsideSRDebug(int fieldID) {
    //profile("DIFC.staticReadBarrierOutsideSR");
    // do nothing: there is no non-debug version of this method
  }

  @Entrypoint @Uninterruptible
  public static final void airavatStaticReadBarrierOutsideSRDebug(int fieldID) {
    // do nothing: there is no non-debug version of this method
  }

  @Entrypoint @Uninterruptible
  public static final void staticWriteBarrierDynamicDebug(int fieldID) {
    if(((GreenThread) Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).getCurrentThread()).inSecureRegion){
      staticWriteBarrierInsideSRDebug(fieldID);
    } else {
      staticWriteBarrierOutsideSRDebug(fieldID);
    }
  }
  
  @Entrypoint @Uninterruptible
  public static final void airavatStaticWriteBarrierDynamicDebug(int fieldID) {
    if (Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).inMapperRegion) {
      airavatStaticWriteBarrierInsideSRDebug(fieldID);
    } else {
      airavatStaticWriteBarrierOutsideSRDebug(fieldID);
    }
  }
  
  @Entrypoint @Inline @Uninterruptible
  public static final void staticWriteBarrierDynamic(int fieldID) {
    if(((GreenThread) Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).getCurrentThread()).inSecureRegion){
      staticWriteBarrierInsideSR(fieldID);
    } else {
      //staticWriteBarrierOutsideSR(fieldID);
    }
  }
  
  @Entrypoint @Inline @Uninterruptible
  public static final void airavatStaticWriteBarrierDynamic(int fieldID) {
    if (Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).inMapperRegion) {
      airavatStaticWriteBarrierInsideSR(fieldID);
    } //else {
      //airavatStaticWriteBarrierOutsideSR(fieldID);
    //}
  }
  
  @Entrypoint @Uninterruptible
  public static final void staticWriteBarrierInsideSRDebug(int fieldID) {
    //profile("DIFC.staticWriteBarrierInsideSR");
    staticWriteBarrierInsideSR(fieldID);
  }

  @Entrypoint @Uninterruptible
  public static final void airavatStaticWriteBarrierInsideSRDebug(int fieldID) {
    // do nothing: there is no non-debug version of this method
    throwAiravatException("Writes to statics inside the mapper is not allowed");
  }

  @Entrypoint @Inline @Uninterruptible
  public static final void staticWriteBarrierInsideSR(int fieldID) {
    staticBarrierSlowPath(fieldID, true);
  }

  @Entrypoint @Inline @Uninterruptible
  public static final void airavatStaticWriteBarrierInsideSR(int fieldID) {
    /*we do not allow writes to statics inside the mapper*/
    throwAiravatException("Writes to statics inside the mapper is not allowed");
  }

  @Entrypoint @Uninterruptible
  public static final void staticWriteBarrierOutsideSRDebug(int fieldID) {
    // do nothing: there is no non-debug version of this method
  }

  @Entrypoint @Uninterruptible
  public static final void airavatStaticWriteBarrierOutsideSRDebug(int fieldID) {
    // do nothing: there is no non-debug version of this method
  }

  @Uninterruptible
  private static final void staticBarrierSlowPath(int fieldID, boolean write) {
    // DIFC: TODO: enforce rules on statics!!
    if (verbosity >= 2) {
      staticBarrierSlowPathHelper(fieldID, write);
    }
  }

  @Uninterruptible
  private static final void staticBarrierSlowPathHelper(int fieldID, boolean write) {
    FieldReference fieldRef = MemberReference.getMemberRef(fieldID).asFieldReference();
    VM.sysWrite("In SR and ");
    if (write) {
      VM.sysWrite("writing");
    } else {
      VM.sysWrite("reading");
    }
    VM.sysWrite(" static field ");
    VM.sysWrite(fieldRef);
    VM.sysWriteln();
    if (verbosity >= 3) {
      VM.sysWrite("Let's dump the stack to see where we are");
      Scheduler.dumpStack();
    }
  }
  
  // allocation barriers: executed between the allocation and the constructor
  
  @Entrypoint @Uninterruptible
  public static final void allocBarrierDynamicDebug(Object obj) {
    if(((GreenThread) Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).getCurrentThread()).inSecureRegion){
      allocBarrierInsideSRDebug(obj);
    } else {
      allocBarrierOutsideSRDebug(obj);
    }
  }
  
  @Entrypoint @Uninterruptible
  public static final void airavatAllocBarrierDynamicDebug(Object obj) {
    if (Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).inMapperRegion) {
      airavatAllocBarrierInsideSRDebug(obj);
    } else {
      airavatAllocBarrierOutsideSRDebug(obj);
    }
  }
  
  @Entrypoint @Inline @Uninterruptible
  public static final void allocBarrierDynamic(Object obj) {
    if(((GreenThread) Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).getCurrentThread()).inSecureRegion){
      allocBarrierInsideSR(obj);
    } else {
      //allocBarrierOutsideSR(obj);
    }
  }
  
  @Entrypoint @Inline @Uninterruptible
  public static final void airavatAllocBarrierDynamic(Object obj) {
    if (Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).inMapperRegion) {
      airavatAllocBarrierInsideSR(obj);
    } 
  }
  
  @Entrypoint @Uninterruptible
  public static final void allocBarrierOutsideSRDebug(Object obj) {
    //profile("DIFC.allocBarrierOutsideSR");
    if (VM.VerifyAssertions) {
	VM._assert(!((GreenThread) Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).getCurrentThread()).inSecureRegion);
      SRState curState=((GreenThread) Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).getCurrentThread()).currentSRState;
      VM._assert(curState.secrecyAllocLabels == LabelSet.EMPTY);
      VM._assert(curState.integrityAllocLabels == LabelSet.EMPTY);
    }
    // there is no non-debug version of this method
  }

  @Entrypoint @Uninterruptible
  public static final void airavatAllocBarrierOutsideSRDebug(Object obj) {
    // there is no non-debug version of this method
  }

  @Entrypoint @Uninterruptible
  public static final void allocBarrierInsideSRDebug(Object obj) {
    //profile("DIFC.allocBarrierInsideSR");
    if (VM.VerifyAssertions) { VM._assert(((GreenThread) Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).getCurrentThread()).inSecureRegion); }
    allocBarrierInsideSR(obj);
  }

  @Entrypoint @Uninterruptible
  public static final void airavatAllocBarrierInsideSRDebug(Object obj) {
    if (VM.VerifyAssertions) { VM._assert(Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).inMapperRegion); }
    airavatAllocBarrierInsideSR(obj);
  }

  @Entrypoint @Inline @Uninterruptible
  public static final void allocBarrierInsideSR(Object obj) {
    // inline this common, cheaper path
    SRState curState=((GreenThread) Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).getCurrentThread()).currentSRState;
    if(curState.secrecyAllocLabels == null &&
        curState.integrityAllocLabels == null){
      //Since the user did not pass labels, lets use the default SR label
      if (MemoryManager.isLabeled(obj)) {
        setObjectLabels(obj, curState.secrecySet, curState.integritySet);
      }
      /*
      if (verbosity >= 2) 
        VM.sysWriteln("using SR's label in alloc");
       */
    } else {
      allocBarrierSlowPath(obj);
    }
  }

  @Entrypoint @Inline @Uninterruptible
  public static final void airavatAllocBarrierInsideSR(Object obj) {
    // inline this common, cheaper path
    if (MemoryManager.isLabeled(obj)) {
      GreenThread currentThread = (GreenThread)GreenProcessor.getCurrentThread();
      setObjectLabels(obj, currentThread.invocationAllocLabel, LabelSet.EMPTY);
    } else{
      VM.sysWriteln("Error: Allocating unlabeled object inside mapper invocation");
    }
  }

  @UninterruptibleNoWarn // since it can throw an exception
  private static final void allocBarrierSlowPath(Object obj) {
    /*
    if (verbosity >= 3) {
      VM.sysWrite("In SR and allocated object ");
      VM.sysWrite(ObjectReference.fromObject(obj));
      VM.sysWrite(" with type ");
      VM.sysWrite(ObjectModel.getObjectType(obj).getDescriptor());
      VM.sysWriteln();
    }
    */
    SRState curState=((GreenThread) Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).getCurrentThread()).currentSRState;
    if(checkAllocLabels()){
      //Use the labels passed by the user 
      if (MemoryManager.isLabeled(obj)) {
        setObjectLabels(obj, curState.secrecyAllocLabels, curState.integrityAllocLabels);
      }
      /*
      if (verbosity >= 2) 
        VM.sysWriteln("using Alloc label in alloc");
      */
    } else {
      throw new DIFCException("Cannot apply passed labels to the allocated object");
    }
  }

  // usually we want to alloc a labeled object inside an SR, but not always!
  @Uninterruptible
  public static boolean shouldAllocLabeledObjectInSR() {
    
    //profile("DIFC.shouldAllocLabeledObjectInSR");

    if (VM.VerifyAssertions) { VM._assert(((GreenThread) Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).getCurrentThread()).inSecureRegion); }
    
    SRState curState=((GreenThread) Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).getCurrentThread()).currentSRState;
    if (curState.secrecySet != null &&
        curState.secrecyAllocLabels != LabelSet.EMPTY) {
      return true;
    }
    return shouldAllocLabeledObjectInSRSlowPath();
  }
  
  @Uninterruptible
  static boolean shouldAllocLabeledObjectInSRSlowPath() {
    
    //profile("DIFC.shouldAllocLabeledObjectInSRSlowPath");

    SRState curState=((GreenThread) Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).getCurrentThread()).currentSRState;
    if (curState.secrecyAllocLabels == null &&
        curState.integrityAllocLabels == null &&
        curState.secrecySet == LabelSet.EMPTY &&
        curState.integritySet == LabelSet.EMPTY) {
      return false;
    }
    if (curState.secrecyAllocLabels == LabelSet.EMPTY &&
        curState.integrityAllocLabels == LabelSet.EMPTY) {
      return false;
    }
    return true;
  }
  
  // DIFC: different types of barriers
  public static final int READ_BARRIER = 1;
  public static final int WRITE_BARRIER = 2;
  public static final int STATIC_READ_BARRIER = 3;
  public static final int STATIC_WRITE_BARRIER = 4;
  public static final int ALLOC_BARRIER = 5;
  
  // Specify where we want to insert barriers
  
  @Uninterruptible
  public static final boolean addBarriers(NormalMethod method) {
    // for now, just put instrumentation in the application (not the libraries)
    if (enabled && VM.difcBarriers) {
      Atom desc = method.getDeclaringClass().getDescriptor();
      boolean app = !desc.isBootstrapClassDescriptor();
      boolean lib = !desc.isRVMDescriptor();
      // handle weird L$Proxy2; classes
      if (desc.getBytes()[1] == '$') {
        return false;
      }
      if ((app && VM.runningVM && VM.fullyBooted) || (lib && VM.difcLibraries)) {
        return true;
      }
    }
    return false;
  }
  
  public static final boolean dynamicBarriers = VM.difcDynamicBarriers;
  
  @Uninterruptible
  public static final NormalMethod addBarriers(NormalMethod method, int type) {
    if (addBarriers(method)) {
      boolean staticOrAllocBarrier =
        (type == STATIC_READ_BARRIER) ||
        (type == STATIC_WRITE_BARRIER) ||
        (type == ALLOC_BARRIER);
      boolean readOrWriteBarrier =
        (type == READ_BARRIER ||
         type == WRITE_BARRIER);
      if ((!VM.difcNoStaticOrAllocBarriers || !staticOrAllocBarrier) &&
          (!VM.difcNoReadOrWriteBarriers   || !readOrWriteBarrier)) {

        if (verbosity >= 3) {
          VM.sysWrite("Adding barrier ");
          VM.sysWrite(type);
          VM.sysWrite(" to ");
          VM.sysWrite(method);
          VM.sysWrite(" while method.inSR = ");
          VM.sysWrite(method.staticallyInSecureRegion);
          VM.sysWriteln();
        }

        // DIFC: TODO: had to remove this
        //if (VM.VerifyAssertions) { VM._assert(VM.runningVM); }
        boolean debugBarriers = VM.VerifyAssertions || /*LabelSet.PROFILE ||*/ VM.difcVerbose;
        
        if (isAiravat) {
          
          if (debugBarriers) {
            if (dynamicBarriers) {
              switch (type) {
              case READ_BARRIER: return Entrypoints.airavatReadBarrierDynamicDebugMethod;
              case WRITE_BARRIER: return Entrypoints.airavatWriteBarrierDynamicDebugMethod;
              case STATIC_READ_BARRIER: return Entrypoints.airavatStaticReadBarrierDynamicDebugMethod;
              case STATIC_WRITE_BARRIER: return Entrypoints.airavatStaticWriteBarrierDynamicDebugMethod;
              case ALLOC_BARRIER: return Entrypoints.airavatAllocBarrierDynamicDebugMethod;
              }
            } else if (method.staticallyInSecureRegion) {
              switch (type) {
              case READ_BARRIER: return Entrypoints.airavatReadBarrierInsideSRDebugMethod;
              case WRITE_BARRIER: return Entrypoints.airavatWriteBarrierInsideSRDebugMethod;
              case STATIC_READ_BARRIER: return Entrypoints.airavatStaticReadBarrierInsideSRDebugMethod;
              case STATIC_WRITE_BARRIER: return Entrypoints.airavatStaticWriteBarrierInsideSRDebugMethod;
              case ALLOC_BARRIER: return Entrypoints.airavatAllocBarrierInsideSRDebugMethod;
              }
            } else {
              switch (type) {
              case READ_BARRIER: return Entrypoints.airavatReadBarrierOutsideSRDebugMethod;
              case WRITE_BARRIER: return Entrypoints.airavatWriteBarrierOutsideSRDebugMethod;
              case STATIC_READ_BARRIER: return Entrypoints.airavatStaticReadBarrierOutsideSRDebugMethod;
              case STATIC_WRITE_BARRIER: return Entrypoints.airavatStaticWriteBarrierOutsideSRDebugMethod;
              case ALLOC_BARRIER: return Entrypoints.airavatAllocBarrierOutsideSRDebugMethod;
              }
            }
          } else {
            if (dynamicBarriers) {
              switch (type) {
              case READ_BARRIER: return Entrypoints.airavatReadBarrierDynamicMethod;
              case WRITE_BARRIER: return Entrypoints.airavatWriteBarrierDynamicMethod;
              case STATIC_READ_BARRIER: return Entrypoints.airavatStaticReadBarrierDynamicMethod;
              case STATIC_WRITE_BARRIER: return Entrypoints.airavatStaticWriteBarrierDynamicMethod;
              case ALLOC_BARRIER: return Entrypoints.airavatAllocBarrierDynamicMethod;
              }
            } else if (method.staticallyInSecureRegion) {
              switch (type) {
              case READ_BARRIER: return Entrypoints.airavatReadBarrierInsideSRMethod;
              case WRITE_BARRIER: return Entrypoints.airavatWriteBarrierInsideSRMethod;
              case STATIC_READ_BARRIER: return Entrypoints.airavatStaticReadBarrierInsideSRMethod;
              case STATIC_WRITE_BARRIER: return Entrypoints.airavatStaticWriteBarrierInsideSRMethod;
              case ALLOC_BARRIER: return Entrypoints.airavatAllocBarrierInsideSRMethod;
              }
            } else {
              switch (type) {
              // not used by Airavat
              }
            }
            
          }
          
        } else {

          if (debugBarriers) {
            if (dynamicBarriers) {
              switch (type) {
              case READ_BARRIER: return Entrypoints.difcReadBarrierDynamicDebugMethod;
              case WRITE_BARRIER: return Entrypoints.difcWriteBarrierDynamicDebugMethod;
              case STATIC_READ_BARRIER: return Entrypoints.difcStaticReadBarrierDynamicDebugMethod;
              case STATIC_WRITE_BARRIER: return Entrypoints.difcStaticWriteBarrierDynamicDebugMethod;
              case ALLOC_BARRIER: return Entrypoints.difcAllocBarrierDynamicDebugMethod;
              }
            } else if (method.staticallyInSecureRegion) {
              switch (type) {
              case READ_BARRIER: return Entrypoints.difcReadBarrierInsideSRDebugMethod;
              case WRITE_BARRIER: return Entrypoints.difcWriteBarrierInsideSRDebugMethod;
              case STATIC_READ_BARRIER: return Entrypoints.difcStaticReadBarrierInsideSRDebugMethod;
              case STATIC_WRITE_BARRIER: return Entrypoints.difcStaticWriteBarrierInsideSRDebugMethod;
              case ALLOC_BARRIER: return Entrypoints.difcAllocBarrierInsideSRDebugMethod;
              }
            } else {
              switch (type) {
              case READ_BARRIER: return Entrypoints.difcReadBarrierOutsideSRDebugMethod;
              case WRITE_BARRIER: return Entrypoints.difcWriteBarrierOutsideSRDebugMethod;
              case STATIC_READ_BARRIER: return Entrypoints.difcStaticReadBarrierOutsideSRDebugMethod;
              case STATIC_WRITE_BARRIER: return Entrypoints.difcStaticWriteBarrierOutsideSRDebugMethod;
              case ALLOC_BARRIER: return Entrypoints.difcAllocBarrierOutsideSRDebugMethod;
              }
            }
          } else {
            if (dynamicBarriers) {
              switch (type) {
              case READ_BARRIER: return Entrypoints.difcReadBarrierDynamicMethod;
              case WRITE_BARRIER: return Entrypoints.difcWriteBarrierDynamicMethod;
              case STATIC_READ_BARRIER: return Entrypoints.difcStaticReadBarrierDynamicMethod;
              case STATIC_WRITE_BARRIER: return Entrypoints.difcStaticWriteBarrierDynamicMethod;
              case ALLOC_BARRIER: return Entrypoints.difcAllocBarrierDynamicMethod;
              }
            } else if (method.staticallyInSecureRegion) {
              switch (type) {
              case READ_BARRIER: return Entrypoints.difcReadBarrierInsideSRMethod;
              case WRITE_BARRIER: return Entrypoints.difcWriteBarrierInsideSRMethod;
              case STATIC_READ_BARRIER: return Entrypoints.difcStaticReadBarrierInsideSRMethod;
              case STATIC_WRITE_BARRIER: return Entrypoints.difcStaticWriteBarrierInsideSRMethod;
              case ALLOC_BARRIER: return Entrypoints.difcAllocBarrierInsideSRMethod;
              }
            } else {
              switch (type) {
              case READ_BARRIER: return Entrypoints.difcReadBarrierOutsideSRMethod;
              case WRITE_BARRIER: return Entrypoints.difcWriteBarrierOutsideSRMethod;
              // these will be null if barrier debugging isn't on
              /*
                case STATIC_READ_BARRIER: return Entrypoints.difcStaticReadBarrierOutsideSRMethod;
                case STATIC_WRITE_BARRIER: return Entrypoints.difcStaticWriteBarrierOutsideSRMethod;
                case ALLOC_BARRIER: return Entrypoints.difcAllocBarrierOutsideSRMethod;
               */
              }
            }
          }
        }
      }
    }
    return null;
  }

  /** Helper method used by the opt compiler */
  public static final NormalMethod addBarriers(Instruction inst, int type) {
    if (!VM.difcNoOptBarriers) {
      InlineSequence position = inst.position;
      if (position != null) {
        NormalMethod method = position.getMethod();
        if (method != null) {
          return addBarriers(method, type);
        }
      }
    }
    return null;
  }

  
    /*Perform label changes if allowed.
     * [S(new) - S(old) \in PlusCap] and [S(old)-S(new) \in MinusCap]
     * Similarly for integrity set. If this is an unlabeled object then setObjectLabel should be called.
     */
    /*TODO: While checking for label use object label= label obj + SR label? */
    public static Object changeObjectLabel(Object obj, LabelSet newSecrecyLabels, LabelSet newIntegrityLabels) throws DIFCException{

      //profile("DIFC.changeObjectLabel");

      // fix new labels if necessary
      if(newSecrecyLabels==null) newSecrecyLabels=LabelSet.EMPTY;
      if(newIntegrityLabels==null) newIntegrityLabels=LabelSet.EMPTY;
      LabelSet oldSecrecyLabels=LabelSet.EMPTY;
      LabelSet oldIntegrityLabels=LabelSet.EMPTY;
      if(!MemoryManager.isLabeled(obj)){
        if(newSecrecyLabels==LabelSet.EMPTY && newIntegrityLabels==LabelSet.EMPTY)
          return obj;
      }else{
        // get old labels
        oldSecrecyLabels = getSecrecyLabels(obj);
        oldIntegrityLabels = getIntegrityLabels(obj);
      }
      // check this is okay
      SRState curState=((GreenThread) Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).getCurrentThread()).currentSRState;
      if(!newSecrecyLabels.checkInUnion(oldSecrecyLabels, curState.plusCapabilitySet))
        throw new DIFCException("Label change not allowed - lacks capability to add all secrecy labels");
      if(!oldSecrecyLabels.checkInUnion(newSecrecyLabels, curState.minusCapabilitySet))
        throw new DIFCException("Label change not allowed - lacks capability to remove all secrecy labels");
      if(!newIntegrityLabels.checkInUnion(oldIntegrityLabels, curState.plusCapabilitySet))
        throw new DIFCException("Label change not allowed - lacks capability to add all integrity labels");
      if(!oldIntegrityLabels.checkInUnion(newIntegrityLabels, curState.minusCapabilitySet))
        throw new DIFCException("Label change not allowed - lacks capability to remove all integrity labels");

      // if this is not total declassification then label the ret obj
      LabelSet savedSecrecyAllocLabels = curState.secrecyAllocLabels;
      LabelSet savedIntegrityAllocLabels = curState.integrityAllocLabels;
      setAllocationLabelsInternal(newSecrecyLabels, newIntegrityLabels);

      // clone the object
      Object retObj = null;
      if (obj instanceof Cloneable) {
        try {
          Method method = obj.getClass().getMethod("clone");
          retObj = method.invoke(obj);
          //profile("DIFC.changeObjectLabel", "clone");
        } catch (Exception ex) {
          //profile("DIFC.changeObjectLabel", "cloneable but exception");
        }
      } else {
        //profile("DIFC.changeObjectLabel", "not cloneable");
      }
      if (retObj == null) {
        retObj = RuntimeEntrypoints.cloneClass2(obj, Magic.getObjectType(obj));
      }

      // restore the allocation labels
      setAllocationLabelsInternal(savedSecrecyAllocLabels, savedIntegrityAllocLabels);

      return retObj;
    }

    // offsets of the labels
    @Uninterruptible
    @Inline
    public static final Address secrecyAddr(Object o) {
      return ObjectReference.fromObject(o).toAddress().minus(20);
      //return ObjectModel.getObjectEndAddress(o);
    }
    @Uninterruptible
    @Inline
    public static final Address integrityAddr(Object o) {
      return ObjectReference.fromObject(o).toAddress().minus(16);
      //return ObjectModel.getObjectEndAddress(o).plus(Constants.BYTES_IN_WORD);
    }
    //public static final int LABEL_HEADER_BYTES = Constants.BYTES_IN_WORD * 2;

    // for internal use only
    @Inline @Uninterruptible
    private static void setObjectLabels(Object obj, LabelSet secrecyLabels, LabelSet integrityLabels) {

      //profile("DIFC.setObjectLabels");

      // DIFC: TODO: comment out again?
      // assertions commented out to avoid taxing the compiler
      if (VM.VerifyAssertions) {
        VM._assert(MemoryManager.isLabeled(obj));
        VM._assert(secrecyLabels != null);
        VM._assert(integrityLabels != null);
        // commented out this assertion because it's okay to
        // allocate an object in an SR with both label sets EMPTY
        //VM._assert(secrecyLabels != LabelSet.EMPTY || integrityLabels != LabelSet.EMPTY);
      }
      secrecyAddr(obj).store(ObjectReference.fromObject(secrecyLabels));
      integrityAddr(obj).store(ObjectReference.fromObject(integrityLabels));
    }
    
    /** Function that can be used to obtain the secrecy labels of an object */
    @Inline
    @Uninterruptible
    public static LabelSet getSecrecyLabels(Object obj) {
      
      //profile("DIFC.getSecrecyLabels");

      // assertions commented out to avoid taxing optimizing compiler
      //if (VM.VerifyAssertions) { VM._assert(isLabeled(obj)); }
      Address addr = ObjectReference.fromObject(obj).toAddress();
      LabelSet labelSet = (LabelSet)secrecyAddr(obj).loadObjectReference().toObject();
      //if (VM.VerifyAssertions) { VM._assert(labelSet != null); }
      return labelSet;
    }

    /** Function that can be used to obtain the secrecy labels of an object */
    @Inline
    @Uninterruptible
    public static LabelSet getIntegrityLabels(Object obj) {
      //profile("DIFC.getIntegrityLabels");
      // assertions commented out to avoid taxing optimizing compiler
      //if (VM.VerifyAssertions) { VM._assert(isLabeled(obj)); }
      Address addr = ObjectReference.fromObject(obj).toAddress();
      LabelSet labelSet = (LabelSet)integrityAddr(obj).loadObjectReference().toObject();
      //if (VM.VerifyAssertions) { VM._assert(labelSet != null); }
      return labelSet;
    }

      
    /*Ask the OS to create and add the new capability
     * We have to map regions to constants used by the OS (difc.h)
     * REGION_NONE  0 : REGION_SELF  1: REGION_GROUP 2
     * */
    public static long OScreateLabel(int type, int region){
      
      //profile("DIFC.OScreateLabel");
	long rv;
	if(region==REGION_SELF)
	    rv = sysCall.sysCreateAndAddLabel(type,1);
	else if(region==REGION_GROUP)
	    rv = sysCall.sysCreateAndAddLabel(type,2);
	else 
	    rv = sysCall.sysCreateAndAddLabel(type,0);
	return rv;
    }
    /*Makes the system call so that the OS drops the capability*/
    public static void OSdropCapability(long[] label, int type, int permanent) throws DIFCException{
      
      //profile("DIFC.OSdropCapability");
      
      int err=sysCall.sysDropCapability(label,label.length,type,permanent); 
      if(err<0)
        throw new DIFCException("The OS could not drop the capability: ENum="+err);
    }

    /*Makes the system call to set labels in the OS */
    public static int OSpassCurrentLabels(){
      
      //profile("DIFC.OSpassCurrentLabels");
      
      /*At the moment there is no point in passing labels till boot completes*/
      if(!BOOT_COMPLETE)
        return 0;
      /*Only secure-regions have labels*/
      if(((GreenThread) Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).getCurrentThread()).inSecureRegion){
	SRState curState=((GreenThread) Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).getCurrentThread()).currentSRState;
        int err=sysCall.sysPassLabels(curState.secrecySet.getLongLabels(),curState.secrecySet.len,curState.integritySet.getLongLabels(),curState.integritySet.len);
        if(err>=0)
          curState.RESTORE_LABELS=true;
        else{
          if (verbosity >= 2) 
            VM.sysWriteln("Failed to pass label: Enum="+err);
        }
        return err;
      }
      /*
      int err=sysCall.sysPassLabels(null,0,null,0);
      if (verbosity >= 3) 
        VM.sysWriteln("Passed null Label: Enum="+err);
      return err;*/
      return 0;
    }

    /*Use special system call to create a labeled directory*/
    public static void OScreateLabeledDirectory(String pathName, int mode, LabelSet secSet, LabelSet intSet) throws DIFCException{
      
      //profile("DIFC.OScreateLabeledDirectory");

      int err=OSpassCurrentLabels();
      if(err<0)
        throw new DIFCException("The OS could not pass labels: ENum="+err);
      if(secSet==null) secSet=LabelSet.EMPTY;
      if(intSet==null) intSet=LabelSet.EMPTY;
      byte[] charName = StringUtilities.stringToBytesNullTerminated(pathName);
      err=sysCall.sysCreateLabeledDirectory(charName, mode, secSet.getLongLabels(), secSet.len, intSet.getLongLabels(), intSet.len);
      if(err<0)
        throw new DIFCException("The OS could not create directory "+pathName+": ENum="+err);
    }

    /*Use special system call to create a labeled file*/
    public static void OScreateLabeledFile(String pathName, int mode, LabelSet secSet, LabelSet intSet) throws DIFCException{
      
      //profile("DIFC.OScreateLabeledFile");

      int err=OSpassCurrentLabels();
      if(err<0)
        throw new DIFCException("The OS could not pass labels: ENum="+err);
      if(secSet==null) secSet=LabelSet.EMPTY;
      if(intSet==null) intSet=LabelSet.EMPTY;
      byte[] charName = StringUtilities.stringToBytesNullTerminated(pathName);
      err=sysCall.sysCreateLabeledFile(charName, mode, secSet.getLongLabels(), secSet.len, intSet.getLongLabels(), intSet.len);
      if(err<0)
        throw new DIFCException("The OS could not create file "+pathName+": ENum="+err);
    }

    /*TODO: just for testing. Should be removed. */
    public static void OSpassMyLabels(LabelSet sSet, LabelSet iSet){
      
      //profile("DIFC.OSpassMyLabels");

      int err=sysCall.sysPassLabels(sSet.getLongLabels(),sSet.len,iSet.getLongLabels(),iSet.len);
      VM.sysWriteln("Passed My Label: Enum="+err);
    }

    /*TODO:trapdoor to initialize the capabilities of a thread. Ideally should be done
     * by the OS modified fork call*/
    public static void initializeThreadCapability(LabelSet capability, int type){
      
      //profile("DIFC.initializeThreadCapability");
	
	if(!((GreenThread) Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).getCurrentThread()).inSecureRegion){
	SRState curState=((GreenThread) Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).getCurrentThread()).currentSRState;
    		if(type==PLUS_CAPABILITY || type==BOTH_CAPABILITY) 
    			curState.plusCapabilitySet=capability;
    		if(type==MINUS_CAPABILITY || type==BOTH_CAPABILITY)
    			curState.minusCapabilitySet=capability;
    	}else
    		throw new DIFCException("Capabilities to the thread should be given only once, at initialization and outside a secure-region");
    }

    // Used to pass the labels that should be put on the newly allocated objects.
    
    /** For the user to call */
    public static void setAllocationLabels(LabelSet sSet, LabelSet iSet){
      if (VM.VerifyAssertions) {
        VM._assert(sSet != null && sSet != LabelSet.EMPTY &&
                   iSet != null && iSet != LabelSet.EMPTY);
      }
      setAllocationLabelsInternal(sSet, iSet);
    }
    
    /** Use the allocation labels of the current SR */
    public static void resetAllocationLabels() {
      setAllocationLabelsInternal(null, null);
    }
    
    private static void setAllocationLabelsInternal(LabelSet sSet, LabelSet iSet){
      
      //profile("DIFC.setAllocationLabelsInternal");

      SRState curState=((GreenThread) Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).getCurrentThread()).currentSRState;
      curState.secrecyAllocLabels = sSet;
      curState.integrityAllocLabels = iSet;
    }

    /*Check that the allocation labels are in accordance to the current Region*/
    /* Check that object has secrecy and integrity follow rules
     * S(new)=S(thread)+{x:x \in plusCapability}
     * I(new)=I(thread)-{x:x \in minusCapability} 
     * */
    @UninterruptibleNoWarn
    private static boolean checkAllocLabels(){
      
      //profile("DIFC.checkAllocLabels");
      
      SRState curState=((GreenThread) Magic.processorAsGreenProcessor(Processor.getCurrentProcessor()).getCurrentThread()).currentSRState;
     // if(GreenProcessor.getCurrentProcessor().inSecureRegion){
        if(!checkAllowed(curState.secrecySet,curState.integritySet,curState.secrecyAllocLabels,curState.integrityAllocLabels))
          throw new DIFCException("Object does not have labels compatible to secure-region");
        if(!curState.secrecyAllocLabels.checkInUnion(curState.secrecySet,curState.plusCapabilitySet))
          throw new DIFCException("Object with secrecy label violation[01] ");
        if(!curState.integritySet.checkInUnion(curState.integrityAllocLabels, curState.minusCapabilitySet))
          throw new DIFCException("Object with integrity label violation[01] ");
      /*}else{
        LabelSet combinedCap=LabelSet.union(curState.plusCapabilitySet, GreenThread.commonPlusCapabilitySet);
        if(!curState.secrecyAllocLabels.isSubsetOf(combinedCap))
            throw new DIFCException("Object with secrecy label violation[2] ");
        if(!curState.integrityAllocLabels.isSubsetOf(combinedCap))
            throw new DIFCException("Object with integrity label violation[2]");
      }*/
      return true;
    }
    
    
    /*Airavat code*/
    public static void startMapInvocation(long invocation){
    }
    public static void endMapInvocation(){
    }
    
    /*Checks if the current mapper invocation can read the variable*/ 
    public static void checkInvocationReadRule(LabelSet invocationSet){
    }
    
    public static long getObjectInvocationNumber(Object obj){
      return 0;
    }
    
    public static long getCurrentInvocationNumber(){
      return 0;
    }
    
    @UninterruptibleNoWarn
    private static void throwAiravatException(String msg) {
      throw new AiravatException(msg);
    }
}
