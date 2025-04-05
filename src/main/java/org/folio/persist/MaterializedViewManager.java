package org.folio.persist;

import io.vertx.core.Future;
import io.vertx.sqlclient.Tuple;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.persist.PostgresClient;

public class MaterializedViewManager {
  private static final Logger logger = LogManager.getLogger();
  private static final Duration CACHE_TTL = Duration.ofMinutes(5);

  private final String matViewName;
  private final long refreshIntervalMs;
  private final String refreshLockTimeout;
  private final String metadataTableName;

  private final AtomicReference<CacheEntry> cache = new AtomicReference<>(null);

  public MaterializedViewManager(String matViewName, long refreshIntervalMs) {
    this.matViewName = matViewName;
    this.refreshIntervalMs = refreshIntervalMs;
    this.refreshLockTimeout = "5 minutes";
    this.metadataTableName = "mat_view_metadata";
  }

  /**
   * Non-blocking check if the materialized view should be used.
   * Uses cache if available and valid.
   */
  public Future<Boolean> shouldUseView(PostgresClient postgresClient) {
    // Check if cache is valid
    CacheEntry currentCache = cache.get();
    if (currentCache != null && currentCache.isValid(CACHE_TTL)) {
      return Future.succeededFuture(currentCache.shouldUseView);
    }

    // Cache is invalid, query the database
    return postgresClient.selectSingle(
        "SELECT last_refresh, is_refreshing FROM "
          + postgresClient.getSchemaName() + "."
          + metadataTableName + " "
          + "WHERE view_name = $1",
        Tuple.of(matViewName)
      )
      .map(row -> {
        boolean result;

        if (row == null) {
          // If no metadata, don't use mat view
          result = false;
        } else {
          OffsetDateTime lastRefresh = row.getOffsetDateTime("last_refresh");
          boolean isRefreshing = row.getBoolean("is_refreshing");

          // If it's refreshing or needs a refresh, don't use it
          boolean needsRefresh = lastRefresh == null
            || lastRefresh.isBefore(OffsetDateTime.now().minus(Duration.ofMillis(refreshIntervalMs)));

          if (needsRefresh && !isRefreshing) {
            // Trigger refresh asynchronously
            triggerRefreshIfNeeded(postgresClient);
          }

          // Use view only if it's fresh and not currently refreshing
          result = !isRefreshing && !needsRefresh;
        }

        // Update cache with new result
        cache.set(new CacheEntry(result));

        return result;
      })
      .recover(err -> {
        logger.error("Error checking materialized view status: " + err.getMessage(), err);
        return Future.succeededFuture(false); // On error, don't use view and don't cache
      });
  }

  /**
   * Invalidates the current cache.
   */
  public void invalidateCache() {
    cache.set(null);
  }

  /**
   * Try to trigger a refresh if needed without blocking.
   * Uses a non-blocking optimistic approach.
   */
  private void triggerRefreshIfNeeded(PostgresClient postgresClient) {
    String instanceId = UUID.randomUUID().toString();

    // Try to acquire the refresh lock without blocking
    postgresClient.execute(
        "UPDATE " + postgresClient.getSchemaName() + "." + metadataTableName + " "
          + "SET is_refreshing = TRUE, "
          + "    refresh_started_at = NOW(), "
          + "    refresh_instance_id = $2 "
          + "WHERE view_name = $1 "
          + "AND (is_refreshing = FALSE OR "
          + "    (refresh_started_at < NOW() - INTERVAL '" + refreshLockTimeout + "')) "
          + "RETURNING view_name",
        Tuple.of(matViewName, instanceId)
      )
      .onSuccess(rs -> {
        // If we got a row back, we acquired the lock
        if (rs != null && rs.rowCount() > 0) {
          // Start refresh process
          startRefreshProcess(postgresClient, instanceId);
        }
      })
      .onFailure(err ->
        logger.error("Failed to update refresh status: " + err.getMessage(), err)
      );
  }

  private Future<Void> startRefreshProcess(PostgresClient postgresClient, String instanceId) {
    return postgresClient.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY "
        + postgresClient.getSchemaName() + "."
        + matViewName)
      .onComplete(ar -> {
        if (ar.succeeded()) {
          postgresClient.execute(
            "UPDATE " + postgresClient.getSchemaName() + "." + metadataTableName + " "
              + "SET last_refresh = NOW(), "
              + "    is_refreshing = FALSE, "
              + "    refresh_instance_id = NULL "
              + "WHERE view_name = $1 AND refresh_instance_id = $2",
            Tuple.of(matViewName, instanceId)
          ).onSuccess(v -> {
            // Invalidate cache when refresh completes successfully
            invalidateCache();
          });
        } else {
          logger.error("Failed to refresh materialized view: " + ar.cause().getMessage(), ar.cause());
          postgresClient.execute(
            "UPDATE " + postgresClient.getSchemaName() + "." + metadataTableName + " "
              + "SET is_refreshing = FALSE "
              + "WHERE view_name = $1 AND refresh_instance_id = $2",
            Tuple.of(matViewName, instanceId)
          );
        }
      })
      .mapEmpty();
  }

  // Cache-related fields
  private static class CacheEntry {
    final boolean shouldUseView;
    final OffsetDateTime timestamp;

    CacheEntry(boolean shouldUseView) {
      this.shouldUseView = shouldUseView;
      this.timestamp = OffsetDateTime.now();
    }

    boolean isValid(Duration ttl) {
      return timestamp.plus(ttl).isAfter(OffsetDateTime.now());
    }
  }
}
