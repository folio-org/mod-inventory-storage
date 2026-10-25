package org.folio.services.customfields;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.Tuple;
import java.util.UUID;
import org.folio.model.RecordUpdate;
import org.folio.rest.jaxrs.model.CustomField;
import org.folio.rest.jaxrs.model.CustomFieldOptionStatistic;
import org.folio.rest.jaxrs.model.CustomFieldStatistic;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.PostgresClient;
import org.folio.service.RecordService;

/**
 * Defers custom field value removal: deleting a field only inserts a row into
 * {@code custom_fields_cleanup}, so the request returns regardless of how many items hold a value.
 * {@link CustomFieldsValueCleanup} removes the values in batches afterwards. The bulk {@code PUT}
 * passes its transaction connection, so there the queue row commits or rolls back together with
 * the definition change. Removes nothing for a removed option, which
 * {@code CleanupGuardCustomFieldsService} allows only while no item holds it. Statistics stay
 * synchronous through the library's implementation.
 */
public class CleanupQueueRecordService implements RecordService {

  private final Vertx vertx;
  private final RecordService statistics;

  public CleanupQueueRecordService(Vertx vertx, RecordService statistics) {
    this.vertx = vertx;
    this.statistics = statistics;
  }

  @Override
  public Future<CustomFieldStatistic> retrieveStatistic(CustomField field, String tenantId) {
    return statistics.retrieveStatistic(field, tenantId);
  }

  @Override
  public Future<CustomFieldOptionStatistic> retrieveOptionStatistic(CustomField field, String optId, String tenantId) {
    return statistics.retrieveOptionStatistic(field, optId, tenantId);
  }

  @Override
  public Future<Void> deleteAllValues(CustomField field, String tenantId) {
    return enqueue(PostgresClient.getInstance(vertx, tenantId)
      .execute(insertSql(tenantId), fieldTuple(field)), tenantId);
  }

  @Override
  public Future<Void> deleteAllValues(Conn conn, CustomField field, String tenantId) {
    return enqueue(conn.execute(insertSql(tenantId), fieldTuple(field)), tenantId);
  }

  @Override
  public Future<Void> deleteMissedOptionValues(RecordUpdate recordUpdate, String tenantId) {
    return Future.succeededFuture();
  }

  private <T> Future<Void> enqueue(Future<T> insert, String tenantId) {
    return insert
      .onSuccess(x -> CustomFieldsValueCleanup.kick(vertx, tenantId))
      .mapEmpty();
  }

  private static String insertSql(String tenantId) {
    return "INSERT INTO " + PostgresClient.convertToPsqlStandard(tenantId)
      + ".custom_fields_cleanup (id, entity_type, ref_id) VALUES ($1, $2, $3)";
  }

  private static Tuple fieldTuple(CustomField field) {
    return Tuple.of(UUID.randomUUID(), field.getEntityType(), field.getRefId());
  }
}
