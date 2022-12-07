package org.folio.rest.support;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

import java.util.concurrent.TimeUnit;

import org.awaitility.core.ConditionFactory;

public class AwaitConfiguration {
  private AwaitConfiguration() { }

  public static ConditionFactory awaitAtMost() {
    // Timeout was gradually extended to try to alleviate instability
    // Attempts should be made to reduce this value
    return await().atMost(10, SECONDS);
  }

  public static ConditionFactory awaitDuring(int timeout, TimeUnit unit) {
    return await().atMost(timeout, unit).during(timeout, unit);
  }
}
