package org.folio.services.customfields;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
class CustomFieldsValueCleanupTest {

  @Test
  @DisplayName("should complete a loop of many asynchronous steps without overflowing the stack")
  void shouldCompleteLongLoop_withoutOverflowingStack(Vertx vertx, VertxTestContext testContext) {
    var steps = 100_000;
    var taken = new AtomicInteger();
    var context = vertx.getOrCreateContext();
    Supplier<Future<Boolean>> step = () -> {
      var next = Promise.<Boolean>promise();
      context.runOnContext(x -> next.complete(taken.incrementAndGet() < steps));
      return next.future();
    };

    context.runOnContext(v -> CustomFieldsValueCleanup.whileMore(step)
      .onComplete(testContext.succeeding(x -> testContext.verify(() -> {
        assertThat(taken.get()).isEqualTo(steps);
        testContext.completeNow();
      }))));
  }

  @Test
  @DisplayName("should end a pass that fails before it starts without throwing to the caller")
  void shouldEndPass_whenItFailsToStart(Vertx vertx) {
    // PostgresClient refuses the default schema as a tenant before any database access
    assertThatNoException().isThrownBy(() -> CustomFieldsValueCleanup.kick(vertx, "public"));
  }
}
