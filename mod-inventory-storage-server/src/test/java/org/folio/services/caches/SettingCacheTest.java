package org.folio.services.caches;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class SettingCacheTest {

  private static final String TEST_CACHE_KEY = "test:SETTING_KEY";
  private static final String TEST_CACHE_VALUE = "test_value";

  private Vertx vertx;
  private SettingCache settingCache;

  @Before
  public void setUp() {
    vertx = Vertx.vertx();
    settingCache = new SettingCache(vertx);
  }

  @Test
  public void shouldRetrieveValueFromCacheUsingMappingFunction(TestContext context) {
    Async async = context.async();
    // Arrange
    BiFunction<String, Executor, CompletableFuture<String>> mappingFunction = (key, executor) ->
      CompletableFuture.completedFuture(TEST_CACHE_VALUE);

    // Act
    Future<String> result = settingCache.get(TEST_CACHE_KEY, mappingFunction);

    // Assert
    result.onComplete(ar -> {
      context.assertTrue(ar.succeeded());
      context.assertEquals(TEST_CACHE_VALUE, ar.result());
      async.complete();
    });
  }

  @Test
  public void shouldCallMappingFunctionWhenKeyNotInCache(TestContext context) {
    Async async = context.async();
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
      context.assertTrue(ar.succeeded());
      context.assertNotNull(mappingFunctionCalled[0]);
      context.assertEquals(TEST_CACHE_KEY, mappingFunctionCalled[0]);
      async.complete();
    });
  }

  @Test
  public void shouldThrowExceptionWhenKeyIsNull() {
    // Arrange
    BiFunction<String, Executor, CompletableFuture<String>> mappingFunction =
      (key, executor) -> CompletableFuture.completedFuture(TEST_CACHE_VALUE);

    // Act & Assert
    assertThrows(
      NullPointerException.class,
      () -> settingCache.get(null, mappingFunction));
  }

  @Test
  public void shouldThrowExceptionWhenMappingFunctionIsNull() {
    // Act & Assert
    assertThrows(
      NullPointerException.class,
      () -> settingCache.get(TEST_CACHE_KEY, null));
  }

  @Test
  public void shouldStoreValueInCache(TestContext context) {
    Async async = context.async();
    // Arrange
    CompletableFuture<String> valueFuture = CompletableFuture.completedFuture(TEST_CACHE_VALUE);

    // Act
    settingCache.put(TEST_CACHE_KEY, valueFuture);

    // Assert
    Future<String> result = settingCache.get(TEST_CACHE_KEY, (key, executor) ->
      CompletableFuture.completedFuture("should_not_use_this"));

    result.onComplete(ar -> {
      context.assertTrue(ar.succeeded());
      context.assertEquals(TEST_CACHE_VALUE, ar.result());
      async.complete();
    });
  }

  @Test
  public void shouldCacheCompletedFutureValue(TestContext context) {
    Async async = context.async();
    // Arrange
    String expectedValue = "cache_test_value";
    CompletableFuture<String> valueFuture = CompletableFuture.completedFuture(expectedValue);

    // Act
    settingCache.put(TEST_CACHE_KEY, valueFuture);

    // Assert - retrieve the cached value
    Future<String> result = settingCache.get(TEST_CACHE_KEY, (key, executor) ->
      CompletableFuture.completedFuture("should_not_use_this"));

    result.onComplete(ar -> {
      context.assertTrue(ar.succeeded());
      context.assertEquals(expectedValue, ar.result());
      async.complete();
    });
  }

  @Test
  public void shouldThrowExceptionWhenValueFutureIsNull() {
    // Act & Assert
    assertThrows(
      NullPointerException.class,
      () -> settingCache.put(TEST_CACHE_KEY, null));
  }

  @Test
  public void shouldReturnFutureObject(TestContext context) {
    Async async = context.async();
    // Arrange
    BiFunction<String, Executor, CompletableFuture<String>> mappingFunction = (key, executor) ->
      CompletableFuture.completedFuture(TEST_CACHE_VALUE);

    // Act
    Future<String> result = settingCache.get(TEST_CACHE_KEY, mappingFunction);

    // Assert
    context.assertNotNull(result);
    assertThat(result, is(notNullValue()));
    async.complete();
  }

  @Test
  public void shouldMappingFunctionReceiveCorrectKey(TestContext context) {
    Async async = context.async();
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
      context.assertTrue(ar.succeeded());
      context.assertEquals(TEST_CACHE_KEY, passedKey[0]);
      async.complete();
    });
  }

  @Test
  public void shouldMultiplePutsAndGetsWorkIndependently(TestContext context) {
    Async async = context.async(2);
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

    result1.onComplete(ar1 -> {
      context.assertTrue(ar1.succeeded());
      context.assertEquals(value1, ar1.result());
      async.countDown();
    });
    result2.onComplete(ar2 -> {
      context.assertTrue(ar2.succeeded());
      context.assertEquals(value2, ar2.result());
      async.countDown();
    });
  }
}
