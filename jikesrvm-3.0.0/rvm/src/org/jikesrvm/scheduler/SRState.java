package org.jikesrvm.scheduler;

import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Uninterruptible;

/*Class that holds the state of a security region, primarily the
 * labels and capabilities. Useful in nested security regions. 
 */
@Uninterruptible
public final class SRState {

  LabelSet secrecySet;
  LabelSet integritySet;
  LabelSet plusCapabilitySet;
  LabelSet minusCapabilitySet;
  LabelSet secrecyAllocLabels;
  LabelSet integrityAllocLabels;
  boolean RESTORE_LABELS; //whether a system call was made in the SR
  //The next and previous states: similar in functionality as in a stack
  SRState child=null;
  SRState parent=null;
  
  public SRState(){
    clearState();
  }
  
  public SRState(LabelSet secrecyAllocLabels, LabelSet integrityAllocLabels){
    clearState();
    this.secrecyAllocLabels = secrecyAllocLabels;
    this.integrityAllocLabels = integrityAllocLabels;
  }
  
  public SRState(LabelSet plusCapabilitySet,LabelSet minusCapabilitySet,
                 LabelSet secrecySet, LabelSet integritySet, boolean restore,
                 LabelSet secrecyAllocLabel, LabelSet integrityAllocLabel){
    setState(plusCapabilitySet, minusCapabilitySet, secrecySet, integritySet, restore, secrecyAllocLabel, integrityAllocLabel);
  }
  
  @Inline
  public final void clearState() {
    setState(LabelSet.EMPTY, LabelSet.EMPTY, LabelSet.EMPTY, LabelSet.EMPTY, false, null, null);
  }
  
  @Inline
  public final void setState(LabelSet plusCapabilitySet,LabelSet minusCapabilitySet,
                       LabelSet secrecySet, LabelSet integritySet, boolean restore,
                       LabelSet secrecyAllocLabel, LabelSet integrityAllocLabel){
      
    this.plusCapabilitySet=plusCapabilitySet;
    this.minusCapabilitySet=minusCapabilitySet;
    this.secrecySet=secrecySet;
    this.integritySet=integritySet;
    this.RESTORE_LABELS=restore;
    this.secrecyAllocLabels=secrecyAllocLabel;
    this.integrityAllocLabels=integrityAllocLabel;
  }
  @Inline
  public LabelSet getSecrecyLabel(){return this.secrecySet;}
  @Inline
  public LabelSet getIntegrityLabel(){return this.integritySet;}
  @Inline
  public LabelSet getPlusCapability(){return this.plusCapabilitySet;}
  @Inline
  public LabelSet getMinusCapability(){return this.minusCapabilitySet;}
  @Inline
  public boolean shouldRestoreLabel(){return this.RESTORE_LABELS;}
  @Inline
  public LabelSet getSecrecyAllocLabel(){return this.secrecyAllocLabels;}
  @Inline
  public LabelSet getIntegrityAllocLabel(){return this.integrityAllocLabels;}

  @Inline
  public void setParentState(SRState p){this.parent=p;}
  @Inline
  public void setChildState(SRState c){this.child=c;}
  @Inline
  public SRState getParentState(){return this.parent;}
  @Inline
  public SRState getChildState(){return this.child;}
  
}
