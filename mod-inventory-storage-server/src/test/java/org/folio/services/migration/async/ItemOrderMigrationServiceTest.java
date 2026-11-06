package org.folio.services.migration.async;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import java.util.Map;
import java.util.Set;
import org.folio.persist.ItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ItemOrderMigrationServiceTest {

  private ItemRepository itemRepository;
  private ItemOrderMigrationService migrationService;

  @BeforeEach
  void setUp() {
    itemRepository = mock(ItemRepository.class);
    migrationService = new ItemOrderMigrationService(itemRepository);
  }

  @Test
  void runMigrationForIds() {
    Set<String> ids = Set.of("holdingsId1", "holdingsId2");
    when(itemRepository.inTransaction(any(), any())).thenReturn(Future.succeededFuture());

    Future<Void> result = migrationService.runMigrationForIds(ids, Map.of());

    assertTrue(result.succeeded());
    verify(itemRepository, times(ids.size())).inTransaction(any(), any());
  }

  @Test
  void processItemsForHoldings() {
    String holdingsId = "holdingsId1";
    RowSet<Row> mockRowSet = mock(RowSet.class);
    when(itemRepository.inTransaction(any(), any())).thenReturn(Future.succeededFuture(mockRowSet));

    Future<Void> result = migrationService.runMigrationForIds(Set.of(holdingsId), Map.of());

    assertTrue(result.succeeded());
    verify(itemRepository).inTransaction(any(), any());
  }

  @Test
  void getMigrationName() {
    assertEquals("itemOrderMigration", migrationService.getMigrationName());
  }
}
