package org.folio.inventory.domain.ingest

import io.vertx.groovy.core.eventbus.EventBus
import io.vertx.groovy.core.eventbus.Message
import org.folio.inventory.domain.Instance
import org.folio.inventory.domain.Item
import org.folio.inventory.domain.Messages
import org.folio.inventory.resources.ingest.IngestJob
import org.folio.inventory.resources.ingest.IngestJobState
import org.folio.inventory.storage.Storage
import org.folio.metadata.common.CollectAll
import org.folio.metadata.common.MessagingContext
import org.folio.metadata.common.domain.Failure

class IngestMessageProcessor {
  private final Storage storage

  IngestMessageProcessor(final Storage storage) {
    this.storage = storage
  }

  void register(EventBus eventBus) {
    eventBus.consumer(Messages.START_INGEST.Address)
      .handler(this.&processRecordsMessage.rcurry(eventBus))

    eventBus.consumer(Messages.INGEST_COMPLETED.Address)
      .handler(this.&markIngestCompleted)
  }

  private void processRecordsMessage(Message message, EventBus eventBus) {
    def allItems = new CollectAll<Item>()
    def allInstances = new CollectAll<Instance>()

    def records = message.body().records
    def materialTypes = message.body().materialTypes

    def context = new MessagingContext(message.headers())

    def instanceCollection = storage.getInstanceCollection(context)
    def itemCollection = storage.getItemCollection(context)

    records.stream()
      .map({
      new Instance(it.title, it.identifiers)
    })
    .forEach({ instanceCollection.add(it, allInstances.receive(),
      { Failure failure -> println("Ingest Creation Failed: ${failure.reason}") })
    })

    allInstances.collect ({ instances ->
      records.stream()
        .map({ record ->
          new Item(
            record.title,
            record.barcode,
            instances.find({ it.title == record.title })?.id,
            "Available", ["id": materialTypes.book], "Main Library")
      })
      .forEach({ itemCollection.add(it, allItems.receive(),
        { Failure failure -> println("Ingest Creation Failed: ${failure.reason}") })
      })
    })

    allItems.collect({
      IngestMessages.completed(context.getHeader("jobId"), context)
        .send(eventBus)
    })
  }

  private void markIngestCompleted(Message message) {
    def context = new MessagingContext(message.headers())

    storage.getIngestJobCollection(context).update(
      new IngestJob(context.getHeader("jobId"), IngestJobState.COMPLETED),
      { },
      { Failure failure ->
        println("Updating ingest job failed: ${failure.reason}") })
  }
}
