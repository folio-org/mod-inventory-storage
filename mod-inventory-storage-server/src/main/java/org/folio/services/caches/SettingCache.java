package org.folio.services.caches;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import org.folio.utils.Environment;

public class SettingCache {

  private static final String EXPIRATION_TIME_PARAM = "cache.setting.expiration.time.seconds";
  private static final int DEFAULT_EXPIRATION_TIME_SECONDS = 86400; // 24 hours

  private static final AtomicReference<SettingCache> INSTANCE = new AtomicReference<>();

  private final AsyncCache<String, String> cache;

  SettingCache(Vertx vertx) {
    int expirationTime = Environment.getIntValue(EXPIRATION_TIME_PARAM, DEFAULT_EXPIRATION_TIME_SECONDS);
    this.cache = Caffeine.newBuilder()
      .expireAfterWrite(expirationTime, TimeUnit.SECONDS)
      .executor(task -> vertx.runOnContext(v -> task.run()))
      .buildAsync();
  }

  /**
   * Returns the shared cache, creating it on first use. Lets integration tests, which cannot reach the
   * verticle context the cache is stored in, overwrite an entry after changing a setting directly in the db.
   */
  public static SettingCache getInstance(Vertx vertx) {
    return INSTANCE.updateAndGet(existing -> existing != null ? existing : new SettingCache(vertx));
  }

  public Future<String> get(String key, BiFunction<String, Executor, CompletableFuture<String>> mappingFunction) {
    return Future.fromCompletionStage(cache.get(key, mappingFunction));
  }

  public void put(String key, CompletableFuture<String> valueFuture) {
    cache.put(key, valueFuture);
  }
}
