package org.folio.rest.support.sql;

import static io.vertx.core.Future.succeededFuture;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowStream;

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
        for (int i = 0; i < numberOfRecords; i++) {
          if (closed) {
            break;
          }

          if (!paused) {
            var row = mock(Row.class);
            var id = UUID.randomUUID();
            when(row.getUUID("id")).thenReturn(id);

            handler.handle(row);
          } else {
            synchronized (this) {
              wait();
            }
          }
        }
      } catch (Exception ex) {
        errorHandler.handle(ex);
      }

      endHandler.handle(null);
    }).start();

    return this;
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
    Promise<Void> promise = Promise.promise();

    close(promise);

    return promise.future();
  }

  @Override
  public void close(Handler<AsyncResult<Void>> completionHandler) {
    closed = true;
    synchronized (this) {
      notifyAll();
    }
    completionHandler.handle(succeededFuture());
  }

}
