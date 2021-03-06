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
package org.mmtk.harness.lang;

import org.vmmagic.unboxed.ObjectReference;

/**
 * An expression that holds a simple value.  Could be a constant or
 * an intermediate value in expression evaluation
 */
public abstract class Value implements Expression {

  public Value eval(Env env) {
    return this;
  }

  /**
   * The result type of this value.
   */
  public abstract Type type();

  /**
   * Copy the value from the given new value.
   */
  public abstract void copyFrom(Value newValue);

  /**
   * Get the value as an integer, failing if this is not an IntValue
   */
  public int getIntValue() {
    throw new RuntimeException("Invalid use of " + type() + " as an integer");
  }

  /**
   * Get the value as a boolean, failing if this is not an BoolValue
   */
  public boolean getBoolValue() {
    throw new RuntimeException("Invalid use of " + type() + " as a boolean");
  }

  /**
   * Get the value as an object, failing if this is not an ObjectValue
   */
  public ObjectReference getObjectValue() {
    throw new RuntimeException("Invalid use of " + type() + " as an object");
  }

  /**
   * Get the value as a string, failing if this is not a StringValue
   */
  public String getStringValue() {
    throw new RuntimeException("Invalid use of " + type() + " as a string");
  }

  /**
   * @see java.lang.Object#clone()
   */
  @Override
  public abstract Value clone();
}
