package org.folio.rest.support;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.ReadStream;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowStream;

/**
 * Converts RowStream[Row] to ReadStream[Buffer]
 *
 */
public class RowStreamToBufferAdapter implements ReadStream<Buffer> {

  private final RowStream<Row> delegate;

  public RowStreamToBufferAdapter(RowStream<Row> delegate) {
    this.delegate = delegate;
  }

  public ReadStream<Buffer> exceptionHandler(Handler<Throwable> exceptionHandler) {
    this.delegate.exceptionHandler(handler -> {
      exceptionHandler.handle(handler);
      delegate.close();
    });
    return this;
  }

  public RowStreamToBufferAdapter handler(Handler<Buffer> handler) {
    if (handler != null) {
      delegate.handler(row -> handler.handle(Buffer.buffer(createJsonFromRow(row))));
    }
    return this;
  }

  public ReadStream<Buffer> pause() {
    this.delegate.pause();
    return this;
  }

  public ReadStream<Buffer> fetch(long amount) {
    this.delegate.fetch(amount);
    return this;
  }

  public ReadStream<Buffer> resume() {
    this.delegate.resume();
    return this;
  }

  public ReadStream<Buffer> endHandler(Handler<Void> endHandler) {
    this.delegate.endHandler(handler -> {
      endHandler.handle(handler);
      delegate.close();
    });
    return this;
  }

  private String createJsonFromRow(Row row) {
    if (row == null) {
      return "";
    }
    JsonObject json = new JsonObject();
    for (int i = 0; i < row.size(); i++) {
      json.put(row.getColumnName(i), convertRowValue(row.getValue(i)));
    }
    return json.toString();
  }

  private Object convertRowValue(Object value) {
    if (value == null) {
      return "";
    }
    return value instanceof JsonObject ||
      value instanceof JsonArray ? value : value.toString();
  }
}
