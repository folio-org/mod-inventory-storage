package org.folio.support.sql;

import static io.vertx.core.Future.succeededFuture;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowStream;
import io.vertx.sqlclient.Tuple;
import java.util.function.Function;
import org.folio.persist.AbstractRepository;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.PostgresClient;

/**
 * Lets a spied repository run its transactions on the real database, but with the row stream of the test as the
 * result of {@code selectStream}. All other statements of the transaction, for example updating a job, are executed.
 */
public final class StreamingTransactions {
  private StreamingTransactions() { }

  public static void streamRowsInTransaction(AbstractRepository<?> repositorySpy, Vertx vertx,
                                             TestRowStream rowStream) {
    doAnswer(invocation -> {
      String tenantId = invocation.getArgument(0);
      Function<Conn, Future<Object>> function = invocation.getArgument(1);
      return PostgresClient.getInstance(vertx, tenantId)
        .withTrans(conn -> function.apply(streaming(conn, rowStream)));
    }).when(repositorySpy).inTransaction(any(), any());
  }

  private static Conn streaming(Conn conn, TestRowStream rowStream) {
    var streaming = mock(Conn.class, delegatesTo(conn));
    doAnswer(invocation -> {
      invocation.<Handler<RowStream<Row>>>getArgument(2).handle(rowStream);
      return succeededFuture();
    }).when(streaming).selectStream(anyString(), any(Tuple.class), any());
    return streaming;
  }
}
