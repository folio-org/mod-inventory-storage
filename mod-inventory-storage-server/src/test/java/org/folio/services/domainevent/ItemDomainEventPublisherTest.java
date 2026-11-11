package org.folio.services.domainevent;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.InventoryKafkaTopic.ITEM;
import static org.folio.InventoryKafkaTopic.REINDEX_RECORDS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.json.JsonObject;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.folio.persist.HoldingsRepository;
import org.folio.persist.InstanceRepository;
import org.folio.persist.ItemRepository;
import org.folio.persist.entity.HoldingsInstance;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;
import org.folio.services.domainevent.model.ItemWithInstanceId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ItemDomainEventPublisherTest {

  private static final String TENANT = "diku";
  private static final Map<String, String> HEADERS = new CaseInsensitiveMap<>(Map.of("X-Okapi-Tenant", TENANT));
  private static final String ITEM_ID = "a8e3ed4f-9d9d-4b4b-8a8a-1b1b1b1b1b1b";
  private static final String HOLDINGS_ID = "b8e3ed4f-9d9d-4b4b-8a8a-1b1b1b1b1b1b";
  private static final String OTHER_HOLDINGS_ID = "c8e3ed4f-9d9d-4b4b-8a8a-1b1b1b1b1b1b";
  private static final String INSTANCE_ID = "d8e3ed4f-9d9d-4b4b-8a8a-1b1b1b1b1b1b";
  private static final String OTHER_INSTANCE_ID = "e8e3ed4f-9d9d-4b4b-8a8a-1b1b1b1b1b1b";
  private static final Map<String, String> SOURCES = Map.of(INSTANCE_ID, "MARC", OTHER_INSTANCE_ID, "CONSORTIUM-MARC");

  private CommonDomainEventPublisher<ItemWithInstanceId> eventPublisher;
  private HoldingsRepository holdingsRepository;
  private ItemRepository itemRepository;
  private ItemDomainEventPublisher publisher;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    eventPublisher = mock(CommonDomainEventPublisher.class);
    holdingsRepository = mock(HoldingsRepository.class);
    itemRepository = mock(ItemRepository.class);
    var factory = mock(DomainEventPublisherFactory.class);
    when(factory.<ItemWithInstanceId>publisher(ITEM)).thenReturn(eventPublisher);
    var reindexPublisher = mock(CommonDomainEventPublisher.class);
    when(factory.reindexPublisher(REINDEX_RECORDS)).thenReturn(reindexPublisher);
    var instanceRepository = mock(InstanceRepository.class);
    when(instanceRepository.getSourcesByIds(anyCollection(), eq(TENANT))).thenReturn(succeededFuture(SOURCES));
    publisher = new ItemDomainEventPublisher(factory, holdingsRepository, instanceRepository, itemRepository);
  }

  @Test
  @DisplayName("should add the instance id and source to the item create event")
  @SuppressWarnings("unchecked")
  void shouldAddInstanceSource_whenItemCreated() {
    // arrange
    var item = item(HOLDINGS_ID);
    mockHoldings(holdings(HOLDINGS_ID, INSTANCE_ID));
    when(eventPublisher.publishRecordsCreated(any(), eq(HEADERS))).thenReturn(succeededFuture());

    // act
    publisher.publishRecordCreated(item, HEADERS);

    // assert
    var captor = ArgumentCaptor.forClass(List.class);
    verify(eventPublisher).publishRecordsCreated(captor.capture(), eq(HEADERS));
    var events = (List<Pair<String, ItemWithInstanceId>>) captor.getValue();
    assertThat(events).singleElement().satisfies(event -> {
      assertThat(event.getKey()).isEqualTo(ITEM_ID);
      assertThat(JsonObject.mapFrom(event.getValue()).getMap())
        .containsEntry("id", ITEM_ID)
        .containsEntry("instanceId", INSTANCE_ID)
        .containsEntry("instanceSource", "MARC");
    });
  }

  @Test
  @DisplayName("should use the old and the new instance source when an item moves between instances")
  @SuppressWarnings("unchecked")
  void shouldUseOldAndNewInstanceSource_whenItemUpdated() {
    // arrange
    final var oldItem = item(HOLDINGS_ID);
    var newItem = item(OTHER_HOLDINGS_ID);
    mockHoldings(holdings(HOLDINGS_ID, INSTANCE_ID), holdings(OTHER_HOLDINGS_ID, OTHER_INSTANCE_ID));
    when(itemRepository.getByIds(any(Collection.class), any(), eq(TENANT)))
      .thenReturn(succeededFuture(Map.of(ITEM_ID, newItem)));
    when(eventPublisher.publishRecordsUpdated(any(), eq(HEADERS))).thenReturn(succeededFuture());

    // act
    publisher.publishRecordUpdated(oldItem, HEADERS);

    // assert
    var captor = ArgumentCaptor.forClass(Collection.class);
    verify(eventPublisher).publishRecordsUpdated(captor.capture(), eq(HEADERS));
    var events = (Collection<Triple<String, ItemWithInstanceId, ItemWithInstanceId>>) captor.getValue();
    assertThat(events).singleElement().satisfies(event -> {
      assertThat(event.getLeft()).isEqualTo(ITEM_ID);
      assertThat(event.getMiddle().getInstanceSource()).isEqualTo("MARC");
      assertThat(event.getRight().getInstanceSource()).isEqualTo("CONSORTIUM-MARC");
    });
  }

  @Test
  @DisplayName("should use the instance sources of both holdings when an item is updated with known holdings")
  void shouldUseHoldingsInstanceSources_whenItemUpdatedWithHoldings() {
    // arrange
    var oldItem = item(HOLDINGS_ID);
    var newItem = item(OTHER_HOLDINGS_ID);
    var oldEvent = ArgumentCaptor.forClass(ItemWithInstanceId.class);
    var newEvent = ArgumentCaptor.forClass(ItemWithInstanceId.class);
    when(eventPublisher.publishRecordUpdated(eq(ITEM_ID), any(), any(), eq(HEADERS))).thenReturn(succeededFuture());

    // act
    publisher.publishUpdated(newItem, oldItem, holdings(OTHER_HOLDINGS_ID, OTHER_INSTANCE_ID),
      holdings(HOLDINGS_ID, INSTANCE_ID), HEADERS);

    // assert
    verify(eventPublisher).publishRecordUpdated(eq(ITEM_ID), oldEvent.capture(), newEvent.capture(), eq(HEADERS));
    assertThat(oldEvent.getValue().getInstanceSource()).isEqualTo("MARC");
    assertThat(newEvent.getValue().getInstanceSource()).isEqualTo("CONSORTIUM-MARC");
  }

  @Test
  @DisplayName("should use the old and the new instance source when holdings move to another instance")
  @SuppressWarnings("unchecked")
  void shouldUseOldAndNewInstanceSource_whenHoldingsMoved() {
    // arrange
    var item = item(HOLDINGS_ID);
    when(itemRepository.getByIds(any(Collection.class), any(), eq(TENANT)))
      .thenReturn(succeededFuture(Map.of(ITEM_ID, item)));
    when(eventPublisher.publishRecordsUpdated(any(), eq(HEADERS))).thenReturn(succeededFuture());

    // act
    publisher.publishUpdated(holdings(HOLDINGS_ID, INSTANCE_ID), holdings(HOLDINGS_ID, OTHER_INSTANCE_ID),
      List.of(item), HEADERS);

    // assert
    var captor = ArgumentCaptor.forClass(Collection.class);
    verify(eventPublisher).publishRecordsUpdated(captor.capture(), eq(HEADERS));
    var events = (Collection<Triple<String, ItemWithInstanceId, ItemWithInstanceId>>) captor.getValue();
    assertThat(events).singleElement().satisfies(event -> {
      assertThat(event.getMiddle().getInstanceId()).isEqualTo(INSTANCE_ID);
      assertThat(event.getMiddle().getInstanceSource()).isEqualTo("MARC");
      assertThat(event.getRight().getInstanceId()).isEqualTo(OTHER_INSTANCE_ID);
      assertThat(event.getRight().getInstanceSource()).isEqualTo("CONSORTIUM-MARC");
    });
  }

  @Test
  @DisplayName("should add the instance source to the item delete event keyed by instance id")
  void shouldAddInstanceSource_whenItemRemoved() {
    // arrange
    var item = item(HOLDINGS_ID);
    mockHoldings(holdings(HOLDINGS_ID, INSTANCE_ID));
    var event = ArgumentCaptor.forClass(ItemWithInstanceId.class);
    when(eventPublisher.publishRecordRemoved(eq(INSTANCE_ID), any(ItemWithInstanceId.class), eq(HEADERS)))
      .thenReturn(succeededFuture());

    // act
    publisher.publishRecordRemoved(item, HEADERS);

    // assert
    verify(eventPublisher).publishRecordRemoved(eq(INSTANCE_ID), event.capture(), eq(HEADERS));
    assertThat(event.getValue().getInstanceSource()).isEqualTo("MARC");
  }

  @Test
  @DisplayName("should fail and publish nothing when the instances of items can't be resolved")
  void shouldFailWithoutPublishing_whenInstanceLookupFails() {
    // arrange
    when(holdingsRepository.getInstancesByHoldingsIds(anyCollection(), eq(TENANT)))
      .thenReturn(failedFuture(new IllegalStateException("db is down")));

    // act
    var result = publisher.publishRecordCreated(item(HOLDINGS_ID), HEADERS);

    // assert
    assertThat(result.failed()).isTrue();
    verify(eventPublisher, never()).publishRecordsCreated(any(), any());
  }

  private void mockHoldings(HoldingsRecord... holdings) {
    var instancesByHoldingsId = new HashMap<String, HoldingsInstance>();
    for (var holdingsRecord : holdings) {
      var instanceId = holdingsRecord.getInstanceId();
      instancesByHoldingsId.put(holdingsRecord.getId(), new HoldingsInstance(instanceId, SOURCES.get(instanceId)));
    }
    when(holdingsRepository.getInstancesByHoldingsIds(anyCollection(), eq(TENANT)))
      .thenReturn(succeededFuture(instancesByHoldingsId));
  }

  private static Item item(String holdingsId) {
    return new Item().withId(ITEM_ID).withHoldingsRecordId(holdingsId);
  }

  private static HoldingsRecord holdings(String id, String instanceId) {
    return new HoldingsRecord().withId(id).withInstanceId(instanceId);
  }
}
