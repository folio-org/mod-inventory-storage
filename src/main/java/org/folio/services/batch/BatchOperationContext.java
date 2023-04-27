package org.folio.services.batch;

import static java.util.Collections.unmodifiableCollection;

import java.util.Collection;

public final class BatchOperationContext<T> {
  private final Collection<T> recordsToBeCreated;
  /**
   * Existing records from database, without update.
   */
  private final Collection<T> existingRecords;

  public BatchOperationContext(Collection<T> recordsToBeCreated, Collection<T> existingRecords) {
    this.recordsToBeCreated = unmodifiableCollection(recordsToBeCreated);
    this.existingRecords = unmodifiableCollection(existingRecords);
  }

  public Collection<T> getRecordsToBeCreated() {
    return recordsToBeCreated;
  }

  public Collection<T> getExistingRecords() {
    return existingRecords;
  }
}
