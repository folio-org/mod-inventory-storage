package org.folio.rest.support;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

import java.util.concurrent.TimeUnit;
import org.awaitility.core.ConditionFactory;

public final class AwaitConfiguration {

  private AwaitConfiguration() { }

  public static ConditionFactory awaitAtMost() {
    // Timeout was gradually extended to try to alleviate instability
    // Attempts should be made to reduce this value
    return await().atMost(20, SECONDS);
  }

  public static ConditionFactory awaitDuring(int timeout, TimeUnit unit) {
    // Uses longer at most than during to avoid known failures
    // this means that it is always possible for a condition to only apply for part of the duration
    // https://stackoverflow.com/questions/62830176/unpredictable-behavior-around-during-and-atmost-of-awaitility
    return await().atMost(timeout + 1, unit).during(timeout, unit);
  }
}
