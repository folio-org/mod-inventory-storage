package org.folio.services.batch;

import static java.util.Collections.unmodifiableList;
import static java.util.stream.Stream.concat;

import java.util.List;
import java.util.stream.Stream;

public final class BatchOperationContext<T>{
  private final List<T> recordsToBeCreated;
  private final List<T> recordsToBeUpdated;
  private final List<T> existingRecordsBeforeUpdate;

  public BatchOperationContext(List<T> recordsToBeCreated, List<T> recordsToBeUpdated,
    List<T> existingRecords) {

    this.recordsToBeCreated = unmodifiableList(recordsToBeCreated);
    this.recordsToBeUpdated = unmodifiableList(recordsToBeUpdated);
    this.existingRecordsBeforeUpdate = unmodifiableList(existingRecords);
  }

  public List<T> getRecordsToBeCreated() {
    return recordsToBeCreated;
  }

  public List<T> getExistingRecordsBeforeUpdate() {
    return existingRecordsBeforeUpdate;
  }

  public Stream<T> allRecordsStream() {
    return concat(recordsToBeCreated.stream(), recordsToBeUpdated.stream());
  }
}
