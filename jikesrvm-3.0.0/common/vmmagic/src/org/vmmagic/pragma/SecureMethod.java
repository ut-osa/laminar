package org.vmmagic.pragma;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.ElementType;

import org.vmmagic.Pragma;

/**
 * DIFC: used to tell the runtime that the given method begins with
 * a call to startSecureRegion and ends with a call to endSecureRegion
 * (possibly with a value returned after endSecureRegion)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Pragma
public @interface SecureMethod { }
