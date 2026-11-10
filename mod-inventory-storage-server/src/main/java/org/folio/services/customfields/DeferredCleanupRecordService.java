package org.folio.services.customfields;

import io.vertx.core.Future;
import org.folio.model.RecordUpdate;
import org.folio.persist.CustomFieldValueRepository;
import org.folio.rest.jaxrs.model.CustomField;
import org.folio.rest.jaxrs.model.CustomFieldOptionStatistic;
import org.folio.rest.jaxrs.model.CustomFieldStatistic;
import org.folio.rest.persist.Conn;
import org.folio.service.RecordService;
import org.folio.services.record.RecordServiceFactoryImpl;

/**
 * Removes no values itself: the trigger on {@code custom_fields} queues the removal of each deleted
 * field's values for {@link CustomFieldsValueCleanup}, and {@code CleanupAwareCustomFieldsService}
 * refuses to remove an option that items hold at the time of the request.
 */
public class DeferredCleanupRecordService implements RecordService {

  private final RecordService statistics;
  private final CustomFieldValueRepository values;

  public DeferredCleanupRecordService(RecordService statistics, CustomFieldValueRepository values) {
    this.statistics = statistics;
    this.values = values;
  }

  @Override
  public Future<CustomFieldStatistic> retrieveStatistic(CustomField field, String tenantId) {
    return statistics.retrieveStatistic(field, tenantId);
  }

  /**
   * Counts the records holding the option as their value or in a list of values, whatever the field's
   * {@code multiSelect} says: a repeatable single select stores a list, and a multi select also accepts a
   * single value. The library counts only the shape that {@code multiSelect} implies.
   */
  @Override
  public Future<CustomFieldOptionStatistic> retrieveOptionStatistic(CustomField field, String optId, String tenantId) {
    var table = RecordServiceFactoryImpl.ENTITY_TYPE_TABLES.get(field.getEntityType());
    var count = table == null
                ? Future.succeededFuture(0)
                : values.countOptionHolders(tenantId, table, field.getRefId(), optId);
    return count.map(holders -> new CustomFieldOptionStatistic()
      .withCustomFieldId(field.getId())
      .withOptionId(optId)
      .withEntityType(field.getEntityType())
      .withCount(holders));
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
