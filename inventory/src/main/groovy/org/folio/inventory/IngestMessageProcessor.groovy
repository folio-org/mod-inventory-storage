package org.folio.inventory

import io.vertx.groovy.core.eventbus.EventBus
import io.vertx.groovy.core.eventbus.Message
import org.folio.inventory.domain.CollectionProvider
import org.folio.inventory.domain.Instance
import org.folio.inventory.domain.Item
import org.folio.inventory.resources.ingest.IngestJob
import org.folio.inventory.resources.ingest.IngestJobState
import org.folio.metadata.common.CollectAll

class IngestMessageProcessor {
  private final CollectionProvider collectionProvider

  IngestMessageProcessor(CollectionProvider collectionProvider) {

    this.collectionProvider = collectionProvider
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

    def tenantId = message.headers().get("tenantId")

    def instanceCollection = collectionProvider.getInstanceCollection(tenantId)
    def itemCollection = collectionProvider.getItemCollection(tenantId)

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
        ["headers" : ["jobId" : message.headers().get("jobId"),
                    "tenantId": message.headers().get("tenantId")]])
    })
  }

  private void markIngestCompleted(Message message) {
    def jobId = message.headers().get("jobId")
    def tenantId = message.headers().get("tenantId")

    completeJob(jobId, tenantId)
  }

  private void completeJob(jobId, tenantId) {
    collectionProvider.getIngestJobCollection(tenantId).update(
      new IngestJob(jobId, IngestJobState.COMPLETED),
      { })
  }
}
