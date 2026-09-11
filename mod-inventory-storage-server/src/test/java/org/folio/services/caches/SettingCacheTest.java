package org.folio.services.caches;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
class SettingCacheTest {

  private static final String TEST_CACHE_KEY = "test:SETTING_KEY";
  private static final String TEST_CACHE_VALUE = "test_value";

  private Vertx vertx;
  private SettingCache settingCache;

  @BeforeEach
  void setUp() {
    vertx = Vertx.vertx();
    settingCache = new SettingCache(vertx);
  }

  @Test
  void shouldRetrieveValueFromCacheUsingMappingFunction(VertxTestContext context) {
    // Arrange
    BiFunction<String, Executor, CompletableFuture<String>> mappingFunction = (key, executor) ->
      CompletableFuture.completedFuture(TEST_CACHE_VALUE);

    // Act
    Future<String> result = settingCache.get(TEST_CACHE_KEY, mappingFunction);

    // Assert
    result.onComplete(ar -> {
      context.verify(() -> {
        assertTrue(ar.succeeded());
        assertEquals(TEST_CACHE_VALUE, ar.result());
      });
      context.completeNow();
    });
  }

  @Test
  void shouldCallMappingFunctionWhenKeyNotInCache(VertxTestContext context) {
    // Arrange
    String[] mappingFunctionCalled = {null};
    BiFunction<String, Executor, CompletableFuture<String>> mappingFunction = (key, executor) -> {
      mappingFunctionCalled[0] = key;
      return CompletableFuture.completedFuture(TEST_CACHE_VALUE);
    };

    // Act
    Future<String> result = settingCache.get(TEST_CACHE_KEY, mappingFunction);

    // Assert
    result.onComplete(ar -> {
      context.verify(() -> {
        assertTrue(ar.succeeded());
        assertNotNull(mappingFunctionCalled[0]);
        assertEquals(TEST_CACHE_KEY, mappingFunctionCalled[0]);
      });
      context.completeNow();
    });
  }

  @Test
  void shouldThrowExceptionWhenKeyIsNull() {
    // Arrange
    BiFunction<String, Executor, CompletableFuture<String>> mappingFunction =
      (key, executor) -> CompletableFuture.completedFuture(TEST_CACHE_VALUE);

    // Act & Assert
    assertThrows(
      NullPointerException.class,
      () -> settingCache.get(null, mappingFunction));
  }

  @Test
  void shouldThrowExceptionWhenMappingFunctionIsNull() {
    // Act & Assert
    assertThrows(
      NullPointerException.class,
      () -> settingCache.get(TEST_CACHE_KEY, null));
  }

  @Test
  void shouldStoreValueInCache(VertxTestContext context) {
    // Arrange
    CompletableFuture<String> valueFuture = CompletableFuture.completedFuture(TEST_CACHE_VALUE);

    // Act
    settingCache.put(TEST_CACHE_KEY, valueFuture);

    // Assert
    Future<String> result = settingCache.get(TEST_CACHE_KEY, (key, executor) ->
      CompletableFuture.completedFuture("should_not_use_this"));

    result.onComplete(ar -> {
      context.verify(() -> {
        assertTrue(ar.succeeded());
        assertEquals(TEST_CACHE_VALUE, ar.result());
      });
      context.completeNow();
    });
  }

  @Test
  void shouldCacheCompletedFutureValue(VertxTestContext context) {
    // Arrange
    String expectedValue = "cache_test_value";
    CompletableFuture<String> valueFuture = CompletableFuture.completedFuture(expectedValue);

    // Act
    settingCache.put(TEST_CACHE_KEY, valueFuture);

    // Assert - retrieve the cached value
    Future<String> result = settingCache.get(TEST_CACHE_KEY, (key, executor) ->
      CompletableFuture.completedFuture("should_not_use_this"));

    result.onComplete(ar -> {
      context.verify(() -> {
        assertTrue(ar.succeeded());
        assertEquals(expectedValue, ar.result());
      });
      context.completeNow();
    });
  }

  @Test
  void shouldThrowExceptionWhenValueFutureIsNull() {
    // Act & Assert
    assertThrows(
      NullPointerException.class,
      () -> settingCache.put(TEST_CACHE_KEY, null));
  }

  @Test
  void shouldReturnFutureObject(VertxTestContext context) {
    // Arrange
    BiFunction<String, Executor, CompletableFuture<String>> mappingFunction = (key, executor) ->
      CompletableFuture.completedFuture(TEST_CACHE_VALUE);

    // Act
    Future<String> result = settingCache.get(TEST_CACHE_KEY, mappingFunction);

    // Assert
    assertNotNull(result);
    context.completeNow();
  }

  @Test
  void shouldMappingFunctionReceiveCorrectKey(VertxTestContext context) {
    // Arrange
    String[] passedKey = {null};
    BiFunction<String, Executor, CompletableFuture<String>> mappingFunction = (key, executor) -> {
      passedKey[0] = key;
      return CompletableFuture.completedFuture(TEST_CACHE_VALUE);
    };

    // Act
    Future<String> result = settingCache.get(TEST_CACHE_KEY, mappingFunction);

    // Assert
    result.onComplete(ar -> {
      context.verify(() -> {
        assertTrue(ar.succeeded());
        assertEquals(TEST_CACHE_KEY, passedKey[0]);
      });
      context.completeNow();
    });
  }

  @Test
  void shouldMultiplePutsAndGetsWorkIndependently(VertxTestContext context) {
    // Arrange
    String key1 = "cache:key1";
    String key2 = "cache:key2";
    String value1 = "value1";
    String value2 = "value2";

    // Act
    settingCache.put(key1, CompletableFuture.completedFuture(value1));
    settingCache.put(key2, CompletableFuture.completedFuture(value2));

    // Assert
    Future<String> result1 = settingCache.get(key1, (key, executor) ->
      CompletableFuture.failedFuture(new RuntimeException("Should use cached value")));
    Future<String> result2 = settingCache.get(key2, (key, executor) ->
      CompletableFuture.failedFuture(new RuntimeException("Should use cached value")));

    var checkpoint = context.checkpoint(2);
    assertCachedValue(context, checkpoint, result1, value1);
    assertCachedValue(context, checkpoint, result2, value2);
  }

  private void assertCachedValue(VertxTestContext context, Checkpoint checkpoint,
                                  Future<String> result, String expectedValue) {
    result.onComplete(ar -> {
      context.verify(() -> {
        assertTrue(ar.succeeded());
        assertEquals(expectedValue, ar.result());
      });
      checkpoint.flag();
    });
  }
}
