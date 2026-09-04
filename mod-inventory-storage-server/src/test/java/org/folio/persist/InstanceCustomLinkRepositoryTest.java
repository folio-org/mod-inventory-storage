package org.folio.persist;

import static org.folio.services.instance.InstanceCustomLinkService.INSTANCE_CUSTOM_LINK_TABLE;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowIterator;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import java.util.List;
import java.util.Map;
import org.folio.rest.exceptions.ValidationException;
import org.folio.rest.jaxrs.model.InstanceCustomLink;
import org.folio.rest.persist.Conn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InstanceCustomLinkRepositoryTest {
  private static String MOCK_ID = "aaaa-bbbb";

  Conn conn;

  InstanceCustomLink entity;

  InstanceCustomLinkRepository repository;

  @BeforeEach
  void setUp() {
    conn = mock(Conn.class);
    entity = mock(InstanceCustomLink.class);
    var context = Vertx.vertx().getOrCreateContext();
    var headers = Map.of("X-Okapi-Tenant", "diku");
    repository = new InstanceCustomLinkRepository(context, headers);
    lenient().when(conn.execute(argThat(sql -> sql.startsWith("LOCK TABLE"))))
      .thenReturn(Future.succeededFuture(mock(RowSet.class)));
    lenient().when(conn.save(eq(INSTANCE_CUSTOM_LINK_TABLE), any()))
      .thenReturn(Future.succeededFuture(MOCK_ID));
    lenient().when(conn.update(eq(INSTANCE_CUSTOM_LINK_TABLE), any(), eq(MOCK_ID)))
      .thenReturn(Future.succeededFuture(mock(RowSet.class)));
  }

  @Test
  void createEntityWhenRowCountBelowLimit() {
    setupQueryReturn(9, 0, 0, 0);

    Future<String> result = repository.create(conn, entity);

    assertTrue(result.succeeded());
    verify(conn).save(eq(INSTANCE_CUSTOM_LINK_TABLE), any());
  }

  @Test
  void exceptionWhenRowCountAtLimit() {
    setupQueryReturn(10, 0, 0, 0);

    Future<String> result = repository.create(conn, entity);

    assertTrue(result.failed());
    assertInstanceOf(ValidationException.class, result.cause());
    verify(conn, never()).save(any(), any());
  }

  @Test
  void updateEntityWhenRowCountBelowLimit() {
    setupQueryReturn(9, 0, 0, 0);

    Future<String> result = repository.modify(conn, MOCK_ID, entity);

    assertTrue(result.succeeded());
    verify(conn).update(eq(INSTANCE_CUSTOM_LINK_TABLE), any(), any());
  }

  @Test
  void updateEntityWhenRowCountAtLimit() {
    setupQueryReturn(10, 0, 0, 0);

    Future<String> result = repository.modify(conn, MOCK_ID, entity);

    assertTrue(result.succeeded());
    verify(conn).update(eq(INSTANCE_CUSTOM_LINK_TABLE), any(), any());
  }

  private void setupQueryReturn(int totalCount, int nameCount, int linkTextCount, int baseUrlCount) {
    var row = mock(Row.class);
    when(row.getInteger("total_count")).thenReturn(totalCount);
    when(row.getInteger("name_count")).thenReturn(nameCount);
    when(row.getInteger("linktext_count")).thenReturn(linkTextCount);
    when(row.getInteger("baseurl_count")).thenReturn(baseUrlCount);

    var rowIter = mock(RowIterator.class);
    var listIter = List.of(row).iterator();
    when(rowIter.hasNext()).thenAnswer(invocation -> listIter.hasNext());
    when(rowIter.next()).thenAnswer(invocation -> listIter.next());
    var rowSet = mock(RowSet.class);
    when(rowSet.iterator()).thenReturn(rowIter);

    when(conn.execute(argThat(sql -> sql.contains("COUNT(*)")), any(Tuple.class)))
      .thenReturn(Future.succeededFuture(rowSet));
  }
}
