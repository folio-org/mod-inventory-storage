package org.folio.inventory.storage.memory

import org.folio.inventory.resources.ingest.IngestJob
import org.folio.inventory.domain.ingest.IngestJobCollection
import org.folio.metadata.common.api.request.PagingParameters
import org.folio.metadata.common.domain.Failure
import org.folio.metadata.common.domain.Success
import org.folio.metadata.common.storage.memory.InMemoryCollection

import java.util.function.Consumer

class InMemoryIngestJobCollection implements IngestJobCollection {

  private final collection = new InMemoryCollection<IngestJob>()

  @Override
  void empty(Consumer<Success> completionCallback,
             Consumer<Failure> failureCallback) {
    collection.empty(completionCallback)
  }

  @Override
  void add(IngestJob item,
           Consumer<Success<IngestJob>> resultCallback,
           Consumer<Failure> failureCallback) {
    collection.add(item.copyWithNewId(UUID.randomUUID().toString()),
      resultCallback)
  }

  @Override
  void findById(String id,
                Consumer<Success<IngestJob>> resultCallback,
                Consumer<Failure> failureCallback) {

    collection.findOne({ it.id == id }, resultCallback)
  }

  @Override
  void findAll(PagingParameters pagingParameters,
               Consumer<Success<List<IngestJob>>> resultCallback,
               Consumer<Failure> failureCallback) {

    collection.some(pagingParameters, resultCallback)
  }

  @Override
  void update(IngestJob ingestJob,
             Consumer<Success> completionCallback,
             Consumer<Failure> failureCallback) {
    collection.replace(ingestJob, completionCallback)
  }

  @Override
  void delete(String id,
              Consumer<Success> completionCallback,
              Consumer<Failure> failureCallback) {
    collection.remove(id, completionCallback)
  }
}
