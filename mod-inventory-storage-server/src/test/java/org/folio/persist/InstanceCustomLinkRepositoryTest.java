package org.folio.persist;

import static org.folio.services.instance.InstanceCustomLinkService.INSTANCE_CUSTOM_LINK_TABLE;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowIterator;
import io.vertx.sqlclient.RowSet;
import java.util.List;
import java.util.Map;
import org.folio.rest.exceptions.ValidationException;
import org.folio.rest.jaxrs.model.InstanceCustomLink;
import org.folio.rest.persist.Conn;
import org.junit.jupiter.api.Test;

class InstanceCustomLinkRepositoryTest {
  @Test
  void createEntityWhenRowCountBelowLimit() {
    var row = mock(Row.class);
    when(row.getInteger("linkcount")).thenReturn(9);

    var rowIter = mock(RowIterator.class);
    var listIter = List.of(row).iterator();
    when(rowIter.hasNext()).thenAnswer(invocation -> listIter.hasNext());
    when(rowIter.next()).thenAnswer(invocation -> listIter.next());
    var rowSet = mock(RowSet.class);
    when(rowSet.iterator()).thenReturn(rowIter);

    var conn = mock(Conn.class);
    when(conn.execute(String.format("LOCK TABLE %s IN EXCLUSIVE MODE", INSTANCE_CUSTOM_LINK_TABLE)))
      .thenReturn(Future.succeededFuture(mock(RowSet.class)));
    when(conn.execute(String.format("SELECT COUNT(*) AS linkcount FROM %s", INSTANCE_CUSTOM_LINK_TABLE)))
      .thenReturn(Future.succeededFuture(rowSet));
    when(conn.save(eq(INSTANCE_CUSTOM_LINK_TABLE), any()))
      .thenReturn(Future.succeededFuture("aaaa-bbbb"));

    var context = Vertx.vertx().getOrCreateContext();
    var headers = Map.of("X-Okapi-Tenant", "diku");
    var repository = new InstanceCustomLinkRepository(context, headers);
    var entity = mock(InstanceCustomLink.class);
    Future<String> result = repository.create(conn, entity);

    assertTrue(result.succeeded());
    verify(conn).save(eq(INSTANCE_CUSTOM_LINK_TABLE), any());
  }

  @Test
  void exceptionWhenRowCountAtLimit() {
    var row = mock(Row.class);
    when(row.getInteger("linkcount")).thenReturn(10);

    var rowIter = mock(RowIterator.class);
    var listIter = List.of(row).iterator();
    when(rowIter.hasNext()).thenAnswer(invocation -> listIter.hasNext());
    when(rowIter.next()).thenAnswer(invocation -> listIter.next());
    var rowSet = mock(RowSet.class);
    when(rowSet.iterator()).thenReturn(rowIter);

    var conn = mock(Conn.class);
    when(conn.execute(String.format("LOCK TABLE %s IN EXCLUSIVE MODE", INSTANCE_CUSTOM_LINK_TABLE)))
      .thenReturn(Future.succeededFuture(mock(RowSet.class)));
    when(conn.execute(String.format("SELECT COUNT(*) AS linkcount FROM %s", INSTANCE_CUSTOM_LINK_TABLE)))
      .thenReturn(Future.succeededFuture(rowSet));
    when(conn.save(eq(INSTANCE_CUSTOM_LINK_TABLE), any()))
      .thenReturn(Future.succeededFuture("aaaa-bbbb"));

    var context = Vertx.vertx().getOrCreateContext();
    var headers = Map.of("X-Okapi-Tenant", "diku");
    var repository = new InstanceCustomLinkRepository(context, headers);
    var entity = mock(InstanceCustomLink.class);
    Future<String> result = repository.create(conn, entity);

    assertTrue(result.failed());
    assertInstanceOf(ValidationException.class, result.cause());
    verify(conn, never()).save(any(), any());
  }
}
