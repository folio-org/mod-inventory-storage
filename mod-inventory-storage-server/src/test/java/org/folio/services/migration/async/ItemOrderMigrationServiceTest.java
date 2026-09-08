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
import java.util.Set;
import org.folio.persist.ItemRepository;
import org.folio.rest.persist.PostgresClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ItemOrderMigrationServiceTest {

  private PostgresClient postgresClient;
  private ItemOrderMigrationService migrationService;

  @BeforeEach
  void setUp() {
    postgresClient = mock(PostgresClient.class);
    ItemRepository itemRepository = mock(ItemRepository.class);
    migrationService = new ItemOrderMigrationService(postgresClient, itemRepository);
  }

  @Test
  void runMigrationForIds() {
    Set<String> ids = Set.of("holdingsId1", "holdingsId2");
    when(postgresClient.withTrans(any())).thenReturn(Future.succeededFuture());

    Future<Void> result = migrationService.runMigrationForIds(ids);

    assertTrue(result.succeeded());
    verify(postgresClient, times(ids.size())).withTrans(any());
  }

  @Test
  void processItemsForHoldings() {
    String holdingsId = "holdingsId1";
    RowSet<Row> mockRowSet = mock(RowSet.class);
    when(postgresClient.withTrans(any())).thenReturn(Future.succeededFuture(mockRowSet));

    Future<Void> result = migrationService.runMigrationForIds(Set.of(holdingsId));

    assertTrue(result.succeeded());
    verify(postgresClient).withTrans(any());
  }

  @Test
  void getMigrationName() {
    assertEquals("itemOrderMigration", migrationService.getMigrationName());
  }
}
