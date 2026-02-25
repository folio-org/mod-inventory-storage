package org.folio.rest.support.sql;

import static io.vertx.core.Future.succeededFuture;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowStream;
import java.util.UUID;

public class TestRowStream implements RowStream<Row> {

  private final int numberOfRecords;

  private Handler<Throwable> errorHandler;
  private Handler<Void> endHandler;
  private volatile boolean paused;
  private volatile boolean closed;

  public TestRowStream(int numberOfRecords) {
    this.numberOfRecords = numberOfRecords;
  }

  @Override
  public RowStream<Row> exceptionHandler(Handler<Throwable> handler) {
    this.errorHandler = handler;
    return this;
  }

  @Override
  public RowStream<Row> handler(Handler<Row> handler) {
    new Thread(() -> {
      try {
        processRows(handler);
      } catch (Exception ex) {
        errorHandler.handle(ex);
      }

      endHandler.handle(null);
    }).start();

    return this;
  }

  private void processRows(Handler<Row> handler) throws InterruptedException {
    for (int i = 0; i < numberOfRecords; i++) {
      if (closed) {
        break;
      }

      if (!paused) {
        handleRow(handler);
      } else {
        synchronized (this) {
          wait();
        }
      }
    }
  }

  private void handleRow(Handler<Row> handler) {
    var row = mock(Row.class);
    var id = UUID.randomUUID();
    when(row.getUUID("id")).thenReturn(id);
    when(row.getValue("jsonb")).thenReturn(new JsonObject());
    when(row.getJsonObject(0)).thenReturn(new JsonObject().put("id", id.toString()));
    handler.handle(row);
  }

  @Override
  public RowStream<Row> pause() {
    paused = true;
    return this;
  }

  @Override
  public RowStream<Row> resume() {
    paused = false;
    synchronized (this) {
      notifyAll();
    }
    return this;
  }

  @Override
  public RowStream<Row> endHandler(Handler<Void> endHandler) {
    this.endHandler = endHandler;
    return this;
  }

  @Override
  public RowStream<Row> fetch(long l) {
    return this;
  }

  @Override
  public Future<Void> close() {
    closed = true;
    synchronized (this) {
      notifyAll();
    }
    return succeededFuture();
  }
}
