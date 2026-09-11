package org.folio.support;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

import java.util.concurrent.TimeUnit;
import org.awaitility.core.ConditionFactory;

public final class AwaitConfiguration {

  private AwaitConfiguration() { }

  public static ConditionFactory awaitAtMost() {
    // Was 20s, gradually extended to alleviate instability from the old FakeKafkaConsumer, which
    // only updated its in-memory index on demand within each poll. Now that it's backed by
    // KafkaTestEventCollector's continuously-draining background thread, an
    // already-arrived message matches on the first poll regardless of this timeout, so it no
    // longer needs to be long enough to also cover broker-poll latency - only genuine
    // publish-to-consume lag.
    return await().atMost(5, SECONDS);
  }

  public static ConditionFactory awaitDuring(int timeout, TimeUnit unit) {
    // Uses longer at most than during to avoid known failures
    // this means that it is always possible for a condition to only apply for part of the duration
    // https://stackoverflow.com/questions/62830176/unpredictable-behavior-around-during-and-atmost-of-awaitility
    return await().atMost(timeout + 1, unit).during(timeout, unit);
  }
}
