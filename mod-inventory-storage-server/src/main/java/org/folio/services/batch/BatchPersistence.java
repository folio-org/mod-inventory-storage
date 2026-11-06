package org.folio.services.batch;

import static org.folio.rest.tools.utils.TenantTool.tenantId;

import io.vertx.core.Future;
import java.util.List;
import java.util.Map;
import org.folio.persist.AbstractRepository;
import org.folio.rest.exceptions.RequestEntityTooLargeException;
import org.folio.rest.tools.utils.MetadataUtil;

/**
 * Stores a list of records in one go, all or nothing. Behaves as RMB's {@code PgUtil.postSync} did.
 */
public final class BatchPersistence {

  private BatchPersistence() {
  }

  /**
   * Saves or upserts the records.
   *
   * @param maxEntities fail with {@link RequestEntityTooLargeException} when there are more records, to avoid out
   *                    of memory
   * @param upsert true to update the records that already exist, false to fail when one exists
   * @param optimisticLocking false suppresses optimistic locking, only allowed if the environment says so
   */
  public static <T> Future<Void> save(AbstractRepository<T> repository, List<T> entities, int maxEntities,
                                      boolean upsert, boolean optimisticLocking, Map<String, String> headers) {
    try {
      if (entities != null && entities.size() > maxEntities) {
        return Future.failedFuture(new RequestEntityTooLargeException("Expected a maximum of " + maxEntities
          + " records to prevent out of memory but got " + entities.size()));
      }
      repository.applyOptimisticLocking(entities, optimisticLocking);
      // RestVerticle populates a single record only, not an array of records
      MetadataUtil.populateMetadata(entities, headers);
    } catch (Exception e) {
      return Future.failedFuture(e);
    }
    var tenantId = tenantId(headers);
    return upsert ? repository.upsertBatch(entities, tenantId) : repository.saveBatch(entities, tenantId);
  }
}
