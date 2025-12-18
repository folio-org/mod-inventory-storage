package org.folio.services.migration.async;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.sqlclient.Tuple;
import java.util.List;
import org.folio.rest.jaxrs.model.AffectedEntity;
import org.folio.rest.persist.Conn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ItemOrderMigrationJobRunnerTest {

  private ItemOrderMigrationJobRunner jobRunner;
  private Conn mockConnection;

  @BeforeEach
  void setUp() {
    jobRunner = new ItemOrderMigrationJobRunner();
    mockConnection = mock(Conn.class);
  }

  @Test
  void testGetMigrationName() {
    assertEquals("itemOrderMigration", jobRunner.getMigrationName());
  }

  @Test
  void testGetAffectedEntities() {
    List<AffectedEntity> affectedEntities = jobRunner.getAffectedEntities();
    assertEquals(1, affectedEntities.size());
    assertEquals(AffectedEntity.ITEM, affectedEntities.get(0));
  }

  @Test
  void testOpenStream() {
    String schemaName = "test_schema";
    when(mockConnection.selectStream(anyString(), any(Tuple.class), any())).thenReturn(Future.succeededFuture());

    jobRunner.openStream(schemaName, mockConnection);

    verify(mockConnection).selectStream(eq("SELECT DISTINCT(holdingsrecordid) as id FROM test_schema.item;"),
      any(), any());
  }
}
