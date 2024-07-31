package org.folio.services.domainevent;

import static org.folio.services.domainevent.DomainEventType.REINDEX;

import org.folio.rest.jaxrs.model.PublishReindexRecords;
import org.folio.rest.jaxrs.model.PublishReindexRecords.RecordType;

public class ReindexEventRaw extends DomainEventRaw {

  private final PublishReindexRecords.RecordType recordType;

  public ReindexEventRaw(String oldEntity,
                         String newEntity,
                         DomainEventType type,
                         String tenant,
                         RecordType recordType) {
    super(oldEntity, newEntity, type, tenant);
    this.recordType = recordType;
  }

  public static ReindexEventRaw reindexEvent(String tenant, RecordType recordType, String rawRecord) {
    return new ReindexEventRaw(null, rawRecord, REINDEX, tenant, recordType);
  }

  public RecordType getRecordType() {
    return recordType;
  }

  @Override
  public String toString() {
    return "ReindexEventRaw{"
      + "recordType=" + recordType
      + "} " + super.toString();
  }
}
