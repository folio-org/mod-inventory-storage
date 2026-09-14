package org.folio.services.holding;

import static io.vertx.core.Future.succeededFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;
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
      mockResponseHandler(holdingPublisher);

      publisher.publishHoldingsAndItemEvents(
        List.of(newHolding), Map.of("h-1", oldHolding), Map.of("h-1", itemsBefore));

      verify(itemPublisher).publishUpdated(oldHolding, newHolding, itemsBefore);
    });
  }

  @Test
  @DisplayName("should skip the item update when there is no matching old holding")
  void shouldSkipItemUpdate_whenNoMatchingOldHolding() {
    var newHolding = new HoldingsRecord().withId("h-1");

    withMockedPublishers((publisher, holdingPublisher, itemPublisher) -> {
      mockResponseHandler(holdingPublisher);

      publisher.publishHoldingsAndItemEvents(List.of(newHolding), Map.of(), Map.of());

      verify(itemPublisher, never()).publishUpdated(any(), any(), any());
    });
  }

  @Test
  @DisplayName("should skip the item update when items-before-update are absent")
  void shouldSkipItemUpdate_whenItemsBeforeAreAbsent() {
    var oldHolding = new HoldingsRecord().withId("h-1");
    var newHolding = new HoldingsRecord().withId("h-1");

    withMockedPublishers((publisher, holdingPublisher, itemPublisher) -> {
      mockResponseHandler(holdingPublisher);

      publisher.publishHoldingsAndItemEvents(List.of(newHolding), Map.of("h-1", oldHolding), Map.of());

      verify(itemPublisher, never()).publishUpdated(any(), any(), any());
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
      var handler = mockResponseHandler(holdingPublisher);

      publisher.publishHoldingsAndItemEvents(
        List.of(updatedNew, createdNew), Map.of("h-1", existingOld), Map.of());

      verify(handler).handle(any(Response.class));
      var captor = ArgumentCaptor.forClass(BatchOperationContext.class);
      verify(holdingPublisher).publishCreatedOrUpdated(captor.capture());
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
      publisher.publishUpdatedItems(oldHolding, newHolding, items);

      verify(itemPublisher).publishUpdated(oldHolding, newHolding, items);
    });
  }

  @Test
  @DisplayName("should delegate to the holding publisher when publishing all removed")
  void shouldDelegateToHoldingPublisher_whenPublishingAllRemoved() {
    withMockedPublishers((publisher, holdingPublisher, itemPublisher) -> {
      publisher.publishAllRemoved();

      verify(holdingPublisher).publishAllRemoved();
    });
  }

  @Test
  @DisplayName("should delegate to the holding publisher when publishing created")
  void shouldDelegateToHoldingPublisher_whenPublishingCreated() {
    withMockedPublishers((publisher, holdingPublisher, itemPublisher) -> {
      publisher.publishCreated();

      verify(holdingPublisher).publishCreated();
    });
  }

  @Test
  @DisplayName("should delegate to the holding publisher when publishing a removed record")
  void shouldDelegateToHoldingPublisher_whenPublishingRemovedRecord() {
    var holding = new HoldingsRecord().withId("h-1");

    withMockedPublishers((publisher, holdingPublisher, itemPublisher) -> {
      publisher.publishRemoved(holding);

      verify(holdingPublisher).publishRemoved(holding);
    });
  }

  @Test
  @DisplayName("should delegate to the holding publisher when publishing a raw removed record")
  void shouldDelegateToHoldingPublisher_whenPublishingRawRemovedRecord() {
    withMockedPublishers((publisher, holdingPublisher, itemPublisher) -> {
      publisher.publishRemoved("h-1", "raw");

      verify(holdingPublisher).publishRemoved("h-1", "raw");
    });
  }

  @Test
  @DisplayName("should delegate to the holding publisher when publishing a created-or-updated batch")
  void shouldDelegateToHoldingPublisher_whenPublishingCreatedOrUpdatedBatch() {
    var batch = new BatchOperationContext<>(List.of(), List.<HoldingsRecord>of(), true);

    withMockedPublishers((publisher, holdingPublisher, itemPublisher) -> {
      publisher.publishCreatedOrUpdated(batch);

      verify(holdingPublisher).publishCreatedOrUpdated(batch);
    });
  }

  @Test
  @DisplayName("should delegate to the holding publisher when publishing reindex holdings")
  void shouldDelegateToHoldingPublisher_whenPublishingReindexHoldings() {
    withMockedPublishers((publisher, holdingPublisher, itemPublisher) -> {
      when(holdingPublisher.publishReindexHoldings("range-1", List.of())).thenReturn(succeededFuture());

      publisher.publishReindexHoldings("range-1", List.of());

      verify(holdingPublisher).publishReindexHoldings("range-1", List.of());
    });
  }

  @Test
  @DisplayName("should delegate to the holding publisher when publishing an updated record")
  void shouldDelegateToHoldingPublisher_whenPublishingUpdatedRecord() {
    var holding = new HoldingsRecord().withId("h-1");

    withMockedPublishers((publisher, holdingPublisher, itemPublisher) -> {
      publisher.publishUpdated(holding);

      verify(holdingPublisher).publishUpdated(holding);
    });
  }

  @SuppressWarnings("unchecked")
  private static Handler<Response> mockResponseHandler(HoldingDomainEventPublisher holdingPublisher) {
    var handler = (Handler<Response>) mock(Handler.class);
    when(holdingPublisher.publishCreatedOrUpdated(any())).thenReturn(handler);
    return handler;
  }

  private static void withMockedPublishers(TestBody body) {
    try (var holdingCtor = mockConstruction(HoldingDomainEventPublisher.class);
         var itemCtor = mockConstruction(ItemDomainEventPublisher.class)) {
      var context = Vertx.vertx().getOrCreateContext();
      var publisher = new HoldingsEventPublisher(context, OKAPI_HEADERS);

      body.run(publisher, holdingCtor.constructed().getFirst(), itemCtor.constructed().getFirst());
    }
  }

  private interface TestBody {
    void run(HoldingsEventPublisher publisher, HoldingDomainEventPublisher holdingPublisher,
             ItemDomainEventPublisher itemPublisher);
  }
}
