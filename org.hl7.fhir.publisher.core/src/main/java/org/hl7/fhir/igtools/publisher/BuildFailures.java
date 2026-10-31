package org.hl7.fhir.igtools.publisher;

public class BuildFailures {

  /**
   * Whether a build that failed with this can't be carried on from (in watch mode, or with a complete build instead of a
   * differential one). Not the Errors the core throws when it meets content it doesn't expect, nor a stack overflow
   * from content nested too deeply: another build (or the next save) can deal with those
   */
  public static boolean isFatal(Throwable e) {
    return (e instanceof VirtualMachineError && !(e instanceof StackOverflowError)) || e instanceof LinkageError;
  }
}
