package org.folio.services.batch;

import static java.util.Collections.unmodifiableCollection;

import java.util.Collection;

/**
 * Context for batch operation.
 *
 * @param existingRecords Existing records from database, without update.
 */
public record BatchOperationContext<T>(Collection<T> recordsToBeCreated, Collection<T> existingRecords,
                                       boolean publishEvents) {
  public BatchOperationContext(Collection<T> recordsToBeCreated, Collection<T> existingRecords, boolean publishEvents) {
    this.recordsToBeCreated = unmodifiableCollection(recordsToBeCreated);
    this.existingRecords = unmodifiableCollection(existingRecords);
    this.publishEvents = publishEvents;
  }
}
