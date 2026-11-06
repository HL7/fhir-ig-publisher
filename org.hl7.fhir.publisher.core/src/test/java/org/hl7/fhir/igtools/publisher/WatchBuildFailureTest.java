package org.hl7.fhir.igtools.publisher;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hl7.fhir.exceptions.FHIRException;
import org.junit.jupiter.api.Test;

/**
 * Watch mode keeps watching after a build that failed, so that the next save can fix it. The core throws a plain Error
 * for content it doesn't expect (e.g. a single value read from an element that repeats), and that stopped the publisher
 */
class WatchBuildFailureTest {

  @Test
  void watchingGoesOnAfterWhatTheContentCausedButNotWhenTheJvmIsInTrouble() {
    assertFalse(BuildFailures.isFatal(new FHIRException("bad content")));
    assertFalse(BuildFailures.isFatal(new Error("Attempt to read a single element when there is more than one present (name)")));
    assertFalse(BuildFailures.isFatal(new StackOverflowError()));
    assertTrue(BuildFailures.isFatal(new OutOfMemoryError()));
    assertTrue(BuildFailures.isFatal(new NoClassDefFoundError("org/example/Missing")));
    assertTrue(BuildFailures.isFatal(new ExceptionInInitializerError("static init")));
  }
}
