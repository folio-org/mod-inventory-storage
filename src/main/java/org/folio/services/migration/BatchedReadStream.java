package org.folio.services.migration;

import io.vertx.core.Handler;
import io.vertx.core.streams.ReadStream;
import java.util.ArrayList;
import java.util.List;

public class BatchedReadStream<T> implements ReadStream<List<T>> {
  private static final int DEFAULT_BATCH_SIZE = 100;

  private final ReadStream<T> delegate;
  private final int batchSize;
  private final List<T> buffer;

  private Handler<List<T>> handler;
  private Handler<Throwable> exceptionHandler;

  public BatchedReadStream(ReadStream<T> delegate) {
    this(delegate, DEFAULT_BATCH_SIZE);
  }

  public BatchedReadStream(ReadStream<T> delegate, int batchSize) {
    this.delegate = delegate;
    this.batchSize = batchSize;
    this.buffer = new ArrayList<>(batchSize);
  }

  @Override
  public BatchedReadStream<T> exceptionHandler(Handler<Throwable> handler) {
    this.exceptionHandler = handler;
    delegate.exceptionHandler(handler);
    return this;
  }

  @Override
  public BatchedReadStream<T> handler(Handler<List<T>> handler) {
    if (handler == null) {
      delegate.handler(null);
      return this;
    }

    this.handler = handler;
    delegate.handler(this::processRecord);
    return this;
  }

  @Override
  public BatchedReadStream<T> pause() {
    delegate.pause();
    return this;
  }

  @Override
  public ReadStream<List<T>> resume() {
    delegate.resume();
    return this;
  }

  @Override
  public BatchedReadStream<T> fetch(long amount) {
    delegate.fetch(amount);
    return this;
  }

  @Override
  public BatchedReadStream<T> endHandler(Handler<Void> endHandler) {
    delegate.endHandler(notUsed -> {
      synchronized (this) {
        // If any records remaining - process them
        // Vert.x does not has any way to identify end of stream right now
        // have to process in this way
        // If an error occurred on last handle do not call end handler
        // exception handler should be called in this case
        if (!buffer.isEmpty() && handler != null && !handleBuffer()) {
          return;
        }
        endHandler.handle(notUsed);
      }
    });

    return this;
  }

  private synchronized void processRecord(T r) {
    buffer.add(r);
    if (buffer.size() >= batchSize) {
      handleBuffer();
    }
  }

  /**
   * Handles records that currently in buffer.
   *
   * @return - true if handled successfully, otherwise false.
   */
  private boolean handleBuffer() {
    try {
      handler.handle(List.copyOf(buffer));
      buffer.clear();
      return true;
    } catch (Exception ex) {
      if (exceptionHandler != null) {
        delegate.pause();
        exceptionHandler.handle(ex);
        return false;
      } else {
        throw new IllegalStateException(ex);
      }
    }
  }
}
