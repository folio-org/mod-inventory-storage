package org.folio.rest.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test method or class as covering one or more TestRail test cases.
 *
 * <p>Usage example:
 * <pre>{@code
 * @Test
 * @TestRailCase(627509)
 * void myTest() { ... }
 * }</pre>
 * or for a class:
 * <pre>{@code
 * @TestRailCase({627509, 627510})
 * class MyTestClass { ... }
 * }
 * </pre>
 *
 * <p>The value is the numeric TestRail case ID visible in the case URL:
 * {@code .../cases/view/<id>}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface TestRailCase {

  /** One or more TestRail case IDs covered by this test. */
  long[] value();
}
