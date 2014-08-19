//DIFC: immutable, interned label sets
package org.jikesrvm.scheduler;

import org.jikesrvm.VM;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.NoBoundsCheck;
import org.vmmagic.pragma.NoNullCheck;
import org.vmmagic.pragma.NoSideEffects;
import org.vmmagic.pragma.NonMoving;
import org.vmmagic.pragma.Uninterruptible;

// non-moving because we point to it via field headers
@NonMoving
@Uninterruptible
public final class LabelSet {
  
  public static final LabelSet EMPTY = new LabelSet(new long[0], 0);
  
  private final long[] labels; // guaranteed sorted
  // package-level access since we don't want to expose to programmers
  final int len; // use this instead of labels.length!!  (since we may want to allocate arrays bigger than the space used)
  
  static int newLabelSets;
  
  private LabelSet(long[] labels, int len) {
    //profile("new LabelSet");
    this.labels = labels;
    this.len = len;
    if (VM.VerifyAssertions) { VM._assert(len > 0 || !VM.runningVM, "Use EMPTY instead of creating new empty label set"); }
    newLabelSets++;
  }

  // standard ways to create a new label set (the multi-label sets use
  // union for simplicity -- consider the cases where the program
  // passes in unsorted parameters or duplicate parameters) 

  @Interruptible
  public static LabelSet getLabelSet(long label) {
    return new LabelSet(new long[] { label }, 1);
  }

  @Interruptible
  public static LabelSet getLabelSet(long label1, long label2) {
    return union(getLabelSet(label1), label2);
  }
  
  @Interruptible
  public static LabelSet getLabelSet(long label1, long label2, long label3) {
    return union(getLabelSet(label1, label2), label3);
  }
  
  // allow only package-level access since we don't want to expose to programmers
  @Deprecated // use (final) len field instead
  @Inline
  int size(){ return len; }
  
  // EMPTY is final, so Jikes and programmers should access it directly
  @Deprecated
  @Inline
  public static LabelSet getEmptyLabel(){return EMPTY;}
  
  // restrict accesses to package since programmers shouldn't be able to get this
  @Inline
  long[] getLongLabels(){return labels;}
  
  // DIFC: O(n) test for subset
  // Checks if THIS is a subset of OTHER
  @Inline
  @NoNullCheck @NoBoundsCheck @NoSideEffects
  public boolean isSubsetOf(LabelSet other) {
    // short-circuit check
    if (this == EMPTY) {
      //profile("LabelSet.isSubsetOf", "sc1");
      return true;
    // comment this out since we only return false in error cases
    /*
    } else if (other == EMPTY) {
      profile("LabelSet.isSubsetOf", "sc2");
      return false; // since we know this != EMPTY
    */
    } else if (this == other) {
      //profile("LabelSet.isSubsetOf", "sc1");
      return true;
    }
    return isSubsetOfSlowPath(other);
  }
  
  boolean isSubsetOfSlowPath(LabelSet other) { 
    long[] labels1 = this.labels;
    long[] labels2 = other.labels;
    int pos1, pos2;
    // the check here just checks if we've reached the end of this
    for (pos1 = 0, pos2 = 0; pos1 < this.len; ) {
      if (pos2 == other.len) {
        //profile("LabelSet.isSubsetOf", "false1");
        return false;
      } else if (labels2[pos2] < labels1[pos1]) {
        pos2++;
      } else  if (labels2[pos2] > labels1[pos1]) {
        //profile("LabelSet.isSubsetOf", "false2");
        return false;
      } else {
        // don't include the elements that are in both
        pos1++;
        pos2++;
      }
    }
    //profile("LabelSet.isSubsetOf", "true");
    return true;
    /*
    long[] thisLabels = this.labels;
    long[] otherLabels = other.labels;
    if(this.len<=0) return true;
    if(this.len>other.len) return false;
    long thisIndex, otherIndex; 
    for(otherIndex=0; otherIndex<other.len; otherIndex++){
        if(thisLabels[0]==otherLabels[otherIndex]){
            for(thisIndex=1; thisIndex<this.len &&(thisLabels[thisIndex]==otherLabels[otherIndex+thisIndex]); thisIndex++);
            return (thisIndex==this.len);
        }
    }
    return false;
    */
  }

  /*Since l1 and l2 can have common elements, we will need to remove duplicates
   * This function is slow if length of labels is large, hence should not be used often.
   */
  @Interruptible
  @Inline
  public static LabelSet union(LabelSet l1, LabelSet l2) {
    // this probably happens a lot; also it catches the case where
    // someone unions EMPTY and EMPTY, in which case we want the
    // result to be EMPTY, not a duplicate empty label set 
    if (l2==EMPTY) {
      //profile("LabelSet.union1", "sc1");
      return l1;
    } else if (l1==EMPTY) {
      //profile("LabelSet.union1", "sc2");
      return l2;
    } else if (l1 == l2) {
      //profile("LabelSet.union1", "sc3");
      return l1;
    }
    return unionSlowPath(l1, l2);
  }
  
  @Interruptible
  static LabelSet unionSlowPath(LabelSet l1, LabelSet l2) {
    long[] labels1 = l1.labels;
    long[] labels2 = l2.labels;
    long[] newLabels = new long[l1.len + l2.len];
    int newPos, pos1, pos2;
    for (newPos = 0, pos1 = 0, pos2 = 0; pos1 < l1.len || pos2 < l2.len; newPos++) {
      // pick the lesser (or take both if equal)
      // if one array is exhausted, pick the other
      if (pos1 == l1.len) {
        newLabels[newPos] = labels2[pos2];
        pos2++;
      } else if (pos2 == l2.len) {
        newLabels[newPos] = labels1[pos1];
        pos1++;
      } else if (labels1[pos1] < labels2[pos2]) {
        newLabels[newPos] = labels1[pos1];
        pos1++;
      } else if (labels1[pos1] > labels2[pos2]) {
        newLabels[newPos] = labels2[pos2];
        pos2++;
      } else {
        newLabels[newPos] = labels1[pos1];
        pos1++;
        pos2++;
      }
    }
    
    // skip if an existing label will do
    if (newPos == l1.len) {
      //profile("LabelSet.union1", "sc4");
      return l1;
    } else if (newPos == l2.len) {
      //profile("LabelSet.union1", "sc5");
      return l2;
    }
    
    //profile("LabelSet.union1", "regular");
    return new LabelSet(newLabels, newPos);
  }

  @Interruptible
  public static LabelSet union(LabelSet l1, long l2){
    long[] labels1 = l1.labels;
    long[] newLabels = new long[l1.len + 1];
    int newPos, pos1, pos2;
    for (newPos = 0, pos1 = 0, pos2 = 0; pos1 < l1.len || pos2 < 1; newPos++) {
      // pick the lesser (or take both if equal)
      // if one array is exhausted, pick the other
      if (pos1 == l1.len) {
        newLabels[newPos] = l2;
        pos2++;
      } else if (pos2 == 1) {
        newLabels[newPos] = labels1[pos1];
        pos1++;
      } else if (labels1[pos1] < l2) {
        newLabels[newPos] = labels1[pos1];
        pos1++;
      } else if (labels1[pos1] > l2) {
        newLabels[newPos] = l2;
        pos2++;
      } else {
        newLabels[newPos] = labels1[pos1];
        pos1++;
        pos2++;
      }
    }
    // short-circuit checks
    if (newPos == l1.len) {
      //profile("LabelSet.union2", "sc1");
      return l1;
    }
    //profile("LabelSet.union2", "regular");
    return new LabelSet(newLabels, newPos);
  }
  
  @Interruptible
  @Inline
  public static LabelSet minus(LabelSet l1, LabelSet l2){
    // short-circuit checks
    if (l1 == EMPTY || l2 == EMPTY) {
      //profile("LabelSet.minus", "sc1");
      return l1;
    } else if (l1 == l2) {
      //profile("LabelSet.minus", "sc2");
      return EMPTY;
    }
    return minusSlowPath(l1, l2);
  }
  
  @Interruptible
  static LabelSet minusSlowPath(LabelSet l1, LabelSet l2) {
    long[] labels1 = l1.labels;
    long[] labels2 = l2.labels;
    long[] newLabels = new long[l1.len]; // be conservative
    int newPos, pos1, pos2;
    // only need to wait until we've exhausted the first array
    for (newPos = 0, pos1 = 0, pos2 = 0; pos1 < l1.len; ) {
      // pick the lesser (or take both if equal)
      // if one array is exhausted, pick the other
      if (pos2 == l2.len || labels1[pos1] < labels2[pos2]) {
        newLabels[newPos] = labels1[pos1];
        newPos++;
        pos1++;
      } else if (labels1[pos1] > labels2[pos2]) {
        // don't include the elements from l2
        pos2++;
      } else {
        // don't include the elements that are in both
        pos1++;
        pos2++;
      }
    }
    if (newPos == 0) {
      //profile("LabelSet.minus", "sc4");
      return EMPTY;
    } else {
      //profile("LabelSet.minus", "regular");
      return new LabelSet(newLabels, newPos);
    }
  }
  
  /** Helper function to check if the elements of the caller are part of the first or second sets */
  @Inline
  public final boolean checkInUnion(LabelSet firstSet, LabelSet secondSet){
    // short-circuit checks
    if (this == EMPTY) {
      //profile("LabelSet.checkInUnion", "sc1");
      return true;
    } else if (firstSet == EMPTY) {
      //profile("LabelSet.checkInUnion", "sc2");
      return this.isSubsetOf(secondSet);
    } else if (secondSet == EMPTY) {
      //profile("LabelSet.checkInUnion", "sc3");
      return this.isSubsetOf(firstSet);
    }
    return checkInUnionSlowPath(firstSet, secondSet);
  }
  
  final boolean checkInUnionSlowPath(LabelSet firstSet, LabelSet secondSet) {
    int pos=0,pos1=0,pos2=0, incr=0;
    boolean change=false;
    while(pos<this.len){
      change=false;incr=0;
      if(pos1<firstSet.len){
        if(this.labels[pos]==firstSet.labels[pos1]) incr=1;
        if(firstSet.labels[pos1]<=this.labels[pos] ){
          pos1++; change=true;
        }
      }
      if(pos2<secondSet.len){
        if(this.labels[pos]==secondSet.labels[pos2]) incr=1;
        if(secondSet.labels[pos2]<=this.labels[pos] ){
          pos2++; change=true;
        }
      }
      /*The current value is not present and is smaller than those in the 2 sets*/
      if(!change) {
        //profile("LabelSet.checkInUnion", "false");
        return false;
      }
      pos+=incr;
    }
    //profile("LabelSet.checkInUnion", "true");
    return true;
  }

  
  @Interruptible
  public void printLabels(String msg){
	  System.out.print(msg+":(");
	  for(int i = 0; i < len; i++)
		  System.out.print(labels[i]+",");
	  System.out.println(") : len="+len);
  }
  
  @Override
  @Interruptible
  public String toString() {
    StringBuilder sb = new StringBuilder();
    String delim = "";
    sb.append("{");
    for(int i = 0; i < len; i++) {
      sb.append(delim);
      sb.append(labels[i]);
      delim = ",";
    }
    sb.append("}");
    return sb.toString();
  }

  // Allow this to be called since aggressive compiler optimizations do it
  @Override
  @Interruptible
  public boolean equals(Object o) {
    return super.equals(o);
  }
  
  @Override
  @Interruptible
  public int hashCode() {
    return super.hashCode();
  }
  
  /*
  public static final boolean PROFILE = VM.difcProfile;
  
  private static final HashMapRVM<String,HashMapRVM<String,int[]>> profileMap = new HashMapRVM<String, HashMapRVM<String,int[]>>();
  
  @Inline
  public static final void profile(String op) {
    if (PROFILE) {
      profileHelper(op, "default");
    }
  }
  
  @Inline
  public static final void profile(String op, String subOp) {
    if (PROFILE) {
      profileHelper(op, subOp);
    }
  }
  
  @NoInline
  @UninterruptibleNoWarn
  static void profileHelper(String op, String subOp) {
    if (VM.runningVM) {
      synchronized (profileMap) {
        HashMapRVM<String, int[]> opMap = profileMap.get(op);
        if (opMap == null) {
          opMap = new HashMapRVM<String, int[]>();
          profileMap.put(op, opMap);
        }
        int[] count = opMap.get(subOp);
        if (count == null) {
          count = new int[1];
          opMap.put(subOp, count);
        }
        count[0]++;
      }
    }
  }
  
  public static final class ProfileCallback implements ExitMonitor {
  
    public void notifyExit(int value) {

      ArrayList<String> keys = new ArrayList<String>();
      for (String op : profileMap.keys()) {
        keys.add(op);
      }
      Comparator<String> comp = new Comparator<String> () {

        public int compare(String o1, String o2) {
          int count1 = getTotal(o1);
          int count2 = getTotal(o2);
          if (count1 < count2) {
            return -1;
          } else if (count1 > count2) {
            return 1;
          } else {
            return 0;
          }
        }
        
      };
      Collections.sort(keys, comp);
      
      for (String op : keys) {
        HashMapRVM<String, int[]> opMap = profileMap.get(op);
        System.out.println(op + ":  " + getTotal(op));
        if (opMap.size() > 1) {
          for (String subOp : opMap.keys()) {
            int[] count = opMap.get(subOp);
            System.out.println("  " + subOp + ":  " + count[0]);
          }
        }
      }
    }
    
    int getTotal(String op) {
      int total = 0;
      HashMapRVM<String, int[]> opMap = profileMap.get(op);
      for (String subOp : opMap.keys()) {
        int[] count = opMap.get(subOp);
        total += count[0];
      }
      return total;
    }
    
  }
  */
  
  /*Airavat specific function*/
  public boolean invocationLessThan(long v){
    return (labels[0]<v ? true: false);
  }
  //Check invocation is < v and not equal to the default value c
  public boolean invocationLessAndNotEq(long v, long c){
    return ((labels[0]<v && labels[0]!=c) ? true: false);
  }
  public long getInvocationNumber(){
    if(len>0)
      return labels[0];
    else
      return -1;
   }
}
