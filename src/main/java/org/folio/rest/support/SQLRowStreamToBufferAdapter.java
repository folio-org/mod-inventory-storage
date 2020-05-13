package org.folio.rest.support;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.ReadStream;
import io.vertx.ext.sql.SQLRowStream;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Converts between SQLRowStream, which is ReadStream[JsonObject], to ReadStream[Buffer]
 * Adds commas and opening brackets to the response
 */
public class SQLRowStreamToBufferAdapter implements ReadStream<Buffer> {

  private SQLRowStream delegate;
  private boolean isFirst = true;

  public SQLRowStreamToBufferAdapter(SQLRowStream delegate) {
    this.delegate = delegate;
  }

  public ReadStream<Buffer> exceptionHandler(Handler<Throwable> handler) {
    this.delegate.exceptionHandler(handler);
    return null;
  }

  public SQLRowStreamToBufferAdapter handler(Handler<Buffer> handler) {
    delegate.handler(h -> {
      final StringBuilder data = new StringBuilder();
      if (isFirst){
        data.append("[");
        isFirst = false;
      }
      data.append(h);
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

  public ReadStream<Buffer> addEnding() {

    return this;
  }


  public ReadStream<Buffer> endHandler(Handler<Void> endHandler) {
    this.delegate.endHandler(endHandler);
    return this;
  }
}
