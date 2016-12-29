package org.folio.inventory

import io.vertx.groovy.core.Vertx
import io.vertx.groovy.core.eventbus.EventBus
import io.vertx.groovy.core.eventbus.Message
import org.folio.inventory.domain.Instance
import org.folio.inventory.domain.Item
import org.folio.inventory.resources.ingest.IngestJob
import org.folio.inventory.resources.ingest.IngestJobState
import org.folio.inventory.storage.Storage
import org.folio.metadata.common.CollectAll
import org.folio.metadata.common.MessagingContext

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

    def context = new MessagingContext(message.headers())

    def instanceCollection = storage.getInstanceCollection(context)
    def itemCollection = storage.getItemCollection(context)

    records.stream()
      .map({
      new Instance(it.title)
    })
    .forEach({ instanceCollection.add(it, allInstances.receive()) })

    allInstances.collect ({ instances ->
      records.stream()
        .map({ record ->
        new Item(
          record.title,
          record.barcode,
          instances.find({ it.title == record.title })?.id)
      })
      .forEach({ itemCollection.add(it, allItems.receive()) })
    })

    allItems.collect({
      eventBus.send( Messages.INGEST_COMPLETED.Address, "",
        ["headers" : fromContext(context)])
    })
  }

  private LinkedHashMap<String, String> fromContext(MessagingContext context) {
    ["jobId"        : context.getHeader("jobId"),
     "tenantId"     : context.tenantId,
     "okapiLocation": context.okapiLocation]
  }

  private void markIngestCompleted(Message message) {
    def context = new MessagingContext(message.headers())

    storage.getIngestJobCollection(context).update(
      new IngestJob(context.getHeader("jobId"), IngestJobState.COMPLETED),
      { })
  }
}
