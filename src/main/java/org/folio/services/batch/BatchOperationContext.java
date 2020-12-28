package org.folio.services.batch;

import static java.util.Collections.unmodifiableList;
import static java.util.stream.Stream.concat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

public final class BatchOperationContext<T>{
  private final List<T> recordsToBeCreated;
  private final List<T> recordsToBeUpdated;
  private final List<T> existingRecordsBeforeUpdate;

  public BatchOperationContext(Collection<T> recordsToBeCreated, Collection<T> recordsToBeUpdated,
    Collection<T> existingRecords) {

    this.recordsToBeCreated = new ArrayList<>(recordsToBeCreated);
    this.recordsToBeUpdated = new ArrayList<>(recordsToBeUpdated);
    this.existingRecordsBeforeUpdate = new ArrayList<>(existingRecords);
  }

  public List<T> getRecordsToBeCreated() {
    return unmodifiableList(recordsToBeCreated);
  }

  public List<T> getExistingRecordsBeforeUpdate() {
    return unmodifiableList(existingRecordsBeforeUpdate);
  }

  public Stream<T> allRecordsStream() {
    return concat(recordsToBeCreated.stream(), recordsToBeUpdated.stream());
  }
}
