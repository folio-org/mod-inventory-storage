package org.folio.rest.support;

import java.util.List;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.ReadStream;
import io.vertx.ext.sql.SQLRowStream;

/**
 * Converts between SQLRowStream, which is ReadStream[JsonObject], to ReadStream[Buffer] Adds commas and opening brackets to the
 * response
 */
public class SQLRowStreamToBufferAdapter implements ReadStream<Buffer> {

  private final SQLRowStream delegate;
  private final List<String> columns;
  private boolean isFirst = true;

  public SQLRowStreamToBufferAdapter(SQLRowStream delegate) {
    this.delegate = delegate;
    this.columns = delegate.columns();
  }

  public ReadStream<Buffer> exceptionHandler(Handler<Throwable> handler) {
    this.delegate.exceptionHandler(handler);
    return null;
  }

  public SQLRowStreamToBufferAdapter handler(Handler<Buffer> handler) {
    delegate.handler(row -> {
      final StringBuilder data = new StringBuilder();
      if (isFirst) {
        data.append("[");
        isFirst = false;
      }
      data.append(buildJsonObject(row));
      data.append(",");
      handler.handle(Buffer.buffer(data.toString()));
    });
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
    this.delegate.endHandler(endHandler);
    return this;
  }

  private JsonObject buildJsonObject(JsonArray row) {
    final JsonObject entries = new JsonObject();

    for (int i = 0; i < row.size(); i++) {
      entries.put(columns.get(i), row.getValue(i));
    }

    return entries;
  }
}
