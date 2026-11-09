package org.folio.services.holding;

import static io.vertx.core.Future.succeededFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;
import org.folio.services.batch.BatchOperationContext;
import org.folio.services.domainevent.HoldingDomainEventPublisher;
import org.folio.services.domainevent.ItemDomainEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class HoldingsEventPublisherTest {

  private static final Map<String, String> OKAPI_HEADERS = new CaseInsensitiveMap<>(Map.of("X-Okapi-Tenant", "diku"));

  @Test
  @DisplayName("should publish an item update when the old holding and items-before-update are both present")
  void shouldPublishItemUpdate_whenOldHoldingAndItemsBeforeArePresent() {
    var oldHolding = new HoldingsRecord().withId("h-1");
    var newHolding = new HoldingsRecord().withId("h-1");
    var itemsBefore = List.of(new Item().withId("i-1"));

    withMockedPublishers((publisher, holdingPublisher, itemPublisher) -> {
      mockBatchPublishing(holdingPublisher);

      publisher.publishHoldingsAndItemEvents(
        List.of(newHolding), Map.of("h-1", oldHolding), Map.of("h-1", itemsBefore), OKAPI_HEADERS);

      verify(itemPublisher).publishUpdated(oldHolding, newHolding, itemsBefore, OKAPI_HEADERS);
    });
  }

  @Test
  @DisplayName("should skip the item update when there is no matching old holding")
  void shouldSkipItemUpdate_whenNoMatchingOldHolding() {
    var newHolding = new HoldingsRecord().withId("h-1");

    withMockedPublishers((publisher, holdingPublisher, itemPublisher) -> {
      mockBatchPublishing(holdingPublisher);

      publisher.publishHoldingsAndItemEvents(List.of(newHolding), Map.of(), Map.of(), OKAPI_HEADERS);

      verify(itemPublisher, never()).publishUpdated(any(), any(), any(), any());
    });
  }

  @Test
  @DisplayName("should skip the item update when items-before-update are absent")
  void shouldSkipItemUpdate_whenItemsBeforeAreAbsent() {
    var oldHolding = new HoldingsRecord().withId("h-1");
    var newHolding = new HoldingsRecord().withId("h-1");

    withMockedPublishers((publisher, holdingPublisher, itemPublisher) -> {
      mockBatchPublishing(holdingPublisher);

      publisher.publishHoldingsAndItemEvents(List.of(newHolding), Map.of("h-1", oldHolding), Map.of(),
        OKAPI_HEADERS);

      verify(itemPublisher, never()).publishUpdated(any(), any(), any(), any());
    });
  }

  @Test
  @SuppressWarnings("unchecked")
  @DisplayName("should partition new holdings into created and treat old holdings as existing records")
  void shouldPartitionNewHoldingsIntoCreatedAndOldHoldingsIntoExisting() {
    var existingOld = new HoldingsRecord().withId("h-1");
    var updatedNew = new HoldingsRecord().withId("h-1");
    var createdNew = new HoldingsRecord().withId("h-2");

    withMockedPublishers((publisher, holdingPublisher, itemPublisher) -> {
      mockBatchPublishing(holdingPublisher);

      publisher.publishHoldingsAndItemEvents(
        List.of(updatedNew, createdNew), Map.of("h-1", existingOld), Map.of(), OKAPI_HEADERS);

      var captor = ArgumentCaptor.forClass(BatchOperationContext.class);
      verify(holdingPublisher).publishRecordsCreatedOrUpdated(captor.capture(), eq(OKAPI_HEADERS));
      var batch = (BatchOperationContext<HoldingsRecord>) captor.getValue();
      assertThat(batch.recordsToBeCreated()).containsExactly(createdNew);
      assertThat(batch.existingRecords()).containsExactly(existingOld);
      assertThat(batch.publishEvents()).isTrue();
    });
  }

  @Test
  @DisplayName("should delegate to the item publisher when publishing updated items directly")
  void shouldDelegateToItemPublisher_whenPublishingUpdatedItemsDirectly() {
    var oldHolding = new HoldingsRecord().withId("h-1");
    var newHolding = new HoldingsRecord().withId("h-1");
    var items = List.of(new Item().withId("i-1"));

    withMockedPublishers((publisher, holdingPublisher, itemPublisher) -> {
      publisher.publishUpdatedItems(oldHolding, newHolding, items, OKAPI_HEADERS);

      verify(itemPublisher).publishUpdated(oldHolding, newHolding, items, OKAPI_HEADERS);
    });
  }

  @Test
  @DisplayName("should delegate to the holding publisher when publishing all removed")
  void shouldDelegateToHoldingPublisher_whenPublishingAllRemoved() {
    withMockedPublishers((publisher, holdingPublisher, itemPublisher) -> {
      publisher.publishAllRemoved(OKAPI_HEADERS);

      verify(holdingPublisher).publishAllRemoved(OKAPI_HEADERS);
    });
  }

  @Test
  @DisplayName("should delegate to the holding publisher when publishing a created record")
  void shouldDelegateToHoldingPublisher_whenPublishingCreated() {
    var holding = new HoldingsRecord().withId("h-1");

    withMockedPublishers((publisher, holdingPublisher, itemPublisher) -> {
      publisher.publishRecordCreated(holding, OKAPI_HEADERS);

      verify(holdingPublisher).publishRecordCreated(holding, OKAPI_HEADERS);
    });
  }

  @Test
  @DisplayName("should delegate to the holding publisher when publishing a removed record")
  void shouldDelegateToHoldingPublisher_whenPublishingRemovedRecord() {
    var holding = new HoldingsRecord().withId("h-1");

    withMockedPublishers((publisher, holdingPublisher, itemPublisher) -> {
      publisher.publishRecordRemoved(holding, OKAPI_HEADERS);

      verify(holdingPublisher).publishRecordRemoved(holding, OKAPI_HEADERS);
    });
  }

  @Test
  @DisplayName("should delegate to the holding publisher when publishing a raw removed record")
  void shouldDelegateToHoldingPublisher_whenPublishingRawRemovedRecord() {
    withMockedPublishers((publisher, holdingPublisher, itemPublisher) -> {
      publisher.publishRemoved("h-1", "raw", OKAPI_HEADERS);

      verify(holdingPublisher).publishRemoved("h-1", "raw", OKAPI_HEADERS);
    });
  }

  @Test
  @DisplayName("should delegate to the holding publisher when publishing a created-or-updated batch")
  void shouldDelegateToHoldingPublisher_whenPublishingCreatedOrUpdatedBatch() {
    var batch = new BatchOperationContext<>(List.of(), List.<HoldingsRecord>of(), true);

    withMockedPublishers((publisher, holdingPublisher, itemPublisher) -> {
      publisher.publishCreatedOrUpdated(batch, OKAPI_HEADERS);

      verify(holdingPublisher).publishRecordsCreatedOrUpdated(batch, OKAPI_HEADERS);
    });
  }

  @Test
  @DisplayName("should delegate to the holding publisher when publishing reindex holdings")
  void shouldDelegateToHoldingPublisher_whenPublishingReindexHoldings() {
    withMockedPublishers((publisher, holdingPublisher, itemPublisher) -> {
      when(holdingPublisher.publishReindexHoldings("range-1", List.of(), OKAPI_HEADERS))
        .thenReturn(succeededFuture());

      publisher.publishReindexHoldings("range-1", List.of(), OKAPI_HEADERS);

      verify(holdingPublisher).publishReindexHoldings("range-1", List.of(), OKAPI_HEADERS);
    });
  }

  @Test
  @DisplayName("should delegate to the holding publisher when publishing an updated record")
  void shouldDelegateToHoldingPublisher_whenPublishingUpdatedRecord() {
    var holding = new HoldingsRecord().withId("h-1");

    withMockedPublishers((publisher, holdingPublisher, itemPublisher) -> {
      publisher.publishRecordUpdated(holding, OKAPI_HEADERS);

      verify(holdingPublisher).publishRecordUpdated(holding, OKAPI_HEADERS);
    });
  }

  private static void mockBatchPublishing(HoldingDomainEventPublisher holdingPublisher) {
    when(holdingPublisher.publishRecordsCreatedOrUpdated(any(), any())).thenReturn(succeededFuture());
  }

  private static void withMockedPublishers(TestBody body) {
    var holdingPublisher = mock(HoldingDomainEventPublisher.class);
    var itemPublisher = mock(ItemDomainEventPublisher.class);

    body.run(new HoldingsEventPublisher(holdingPublisher, itemPublisher), holdingPublisher, itemPublisher);
  }

  private interface TestBody {
    void run(HoldingsEventPublisher publisher, HoldingDomainEventPublisher holdingPublisher,
             ItemDomainEventPublisher itemPublisher);
  }
}
