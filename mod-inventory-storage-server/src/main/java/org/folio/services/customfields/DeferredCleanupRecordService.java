package org.folio.services.customfields;

import io.vertx.core.Future;
import org.folio.model.RecordUpdate;
import org.folio.rest.jaxrs.model.CustomField;
import org.folio.rest.jaxrs.model.CustomFieldOptionStatistic;
import org.folio.rest.jaxrs.model.CustomFieldStatistic;
import org.folio.rest.persist.Conn;
import org.folio.service.RecordService;

/**
 * Removes no values itself: the trigger on {@code custom_fields} queues the removal of each deleted
 * field's values for {@link CustomFieldsValueCleanup}, and {@code CleanupAwareCustomFieldsService}
 * refuses to remove an option that items hold at the time of the request.
 */
public class DeferredCleanupRecordService implements RecordService {

  private final RecordService statistics;

  public DeferredCleanupRecordService(RecordService statistics) {
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
    return Future.succeededFuture();
  }

  @Override
  public Future<Void> deleteAllValues(Conn conn, CustomField field, String tenantId) {
    return Future.succeededFuture();
  }

  @Override
  public Future<Void> deleteMissedOptionValues(RecordUpdate recordUpdate, String tenantId) {
    return Future.succeededFuture();
  }
}
