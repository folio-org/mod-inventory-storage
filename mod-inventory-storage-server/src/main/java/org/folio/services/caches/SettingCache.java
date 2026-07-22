package org.folio.services.caches;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import org.folio.utils.Environment;

public class SettingCache {

  private static final String EXPIRATION_TIME_PARAM = "cache.setting.expiration.time.seconds";
  private static final int DEFAULT_EXPIRATION_TIME_SECONDS = 86400; // 24 hours

  private final AsyncCache<String, String> cache;

  public SettingCache(Vertx vertx) {
    int expirationTime = Environment.getIntValue(EXPIRATION_TIME_PARAM, DEFAULT_EXPIRATION_TIME_SECONDS);
    this.cache = Caffeine.newBuilder()
      .expireAfterWrite(expirationTime, TimeUnit.SECONDS)
      .executor(task -> vertx.runOnContext(v -> task.run()))
      .buildAsync();
  }

  public Future<String> get(String key, BiFunction<String, Executor, CompletableFuture<String>> mappingFunction) {
    return Future.fromCompletionStage(cache.get(key, mappingFunction));
  }

  public void put(String key, CompletableFuture<String> valueFuture) {
    cache.put(key, valueFuture);
  }
}
