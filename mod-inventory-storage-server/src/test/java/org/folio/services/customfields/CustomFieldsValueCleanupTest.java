package org.folio.services.customfields;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.folio.services.customfields.CustomFieldsValueCleanup.MAX_FAILED_RECORDS;
import static org.folio.services.customfields.CustomFieldsValueCleanup.READ_SIZE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.folio.persist.CustomFieldsCleanupRepository;
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
  @DisplayName("should fail the loop when a later step throws instead of returning a future")
  void shouldFailLoop_whenLaterStepThrows(Vertx vertx, VertxTestContext testContext) {
    var taken = new AtomicInteger();
    var context = vertx.getOrCreateContext();
    Supplier<Future<Boolean>> step = () -> {
      if (taken.incrementAndGet() == 2) {
        throw new IllegalStateException("step failed");
      }
      var next = Promise.<Boolean>promise();
      context.runOnContext(x -> next.complete(true));
      return next.future();
    };

    context.runOnContext(v -> CustomFieldsValueCleanup.whileMore(step)
      .onComplete(testContext.failing(e -> testContext.verify(() -> {
        assertThat(e).hasMessage("step failed");
        testContext.completeNow();
      }))));
  }

  @Test
  @DisplayName("should stop a removal once the maximum number of records have failed")
  void shouldStopRemoval_whenMaxFailedRecordsReached(Vertx vertx, VertxTestContext testContext) {
    var context = vertx.getOrCreateContext();
    var repository = mock(CustomFieldsCleanupRepository.class);
    var reads = new AtomicInteger();
    var attempts = new AtomicInteger();
    when(repository.readHolders(eq("item"), eq("textbox"), eq(READ_SIZE), anyCollection())).thenAnswer(x -> {
      reads.incrementAndGet();
      List<UUID> ids = Stream.generate(UUID::randomUUID).limit(READ_SIZE).toList();
      return later(context, Future.succeededFuture(ids));
    });
    when(repository.removeValue(eq("item"), eq("textbox"), any())).thenAnswer(x -> {
      attempts.incrementAndGet();
      return later(context, Future.<Integer>failedFuture(new IllegalStateException("cleanup refused")));
    });

    context.runOnContext(v -> CustomFieldsValueCleanup.removeFieldValues(repository, "item", "textbox")
      .onComplete(testContext.failing(e -> testContext.verify(() -> {
        assertThat(e).hasMessageStartingWith(MAX_FAILED_RECORDS
          + " of the records holding the value could not be updated and the removal stopped, first [id=");
        assertThat(e).hasMessageEndingWith("]: cleanup refused");
        assertThat(attempts.get()).isEqualTo(MAX_FAILED_RECORDS);
        assertThat(reads.get()).isEqualTo(MAX_FAILED_RECORDS / READ_SIZE);
        testContext.completeNow();
      }))));
  }

  @Test
  @DisplayName("should end a pass that fails before it starts without throwing to the caller")
  void shouldEndPass_whenItFailsToStart(Vertx vertx) {
    // PostgresClient refuses the default schema as a tenant before any database access
    assertThatNoException().isThrownBy(() -> CustomFieldsValueCleanup.kick(vertx, "public"));
  }

  private static <T> Future<T> later(Context context, Future<T> result) {
    var promise = Promise.<T>promise();
    context.runOnContext(x -> result.onComplete(promise::complete, promise::fail));
    return promise.future();
  }
}
