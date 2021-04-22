package org.folio.services.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.folio.rest.api.TestBase.get;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.streams.ReadStream;
import io.vertx.sqlclient.Row;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.folio.rest.api.ReindexJobRunnerTest;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class BatchedReadStreamTest {
  @Test
  @SuppressWarnings("unchecked")
  public void shouldProcessRecordsByBatch() {
    var numberOfRecords = 410;
    var batchSize = 100;
    var numberOfBatches = 5;
    var rowStream = new ReindexJobRunnerTest.TestRowStream(numberOfRecords);
    var batchedReadStream = new BatchedReadStream<>(rowStream, batchSize);
    var promise = Promise.promise();
    Handler<List<Row>> handler = mock(Handler.class);

    batchedReadStream
      .handler(handler)
      .endHandler(promise::tryComplete)
      .exceptionHandler(promise::tryFail);

    // Wait until completed
    get(promise.future());

    ArgumentCaptor<List<Row>> captor = ArgumentCaptor.forClass(List.class);
    verify(handler, times(numberOfBatches)).handle(captor.capture());

    assertThat(captor.getAllValues()).hasSize(numberOfBatches);
    assertThat(captor.getAllValues().get(4)).hasSize(10);
    captor.getAllValues().stream().limit(numberOfBatches - 1)
      .forEach(list -> assertThat(list).hasSize(batchSize));


    var ids = new HashSet<UUID>(numberOfRecords);
    captor.getAllValues().forEach(
      list -> list.forEach(
        // Make sure all IDS are unique so that batch doe not produce
        // duplicate records
        row -> assertThat(ids.add(row.getUUID("id"))).isTrue()));
  }

  @Test
  public void shouldDoNothingIfNullHandler() {
    var rowStream = mockStream();
    var batchedReadStream = new BatchedReadStream<>(rowStream);

    batchedReadStream.handler(null);

    verifyNoInteractions(rowStream);
  }

  @Test
  public void shouldDelegateCallForExceptionHandler() {
    var delegate = mockStream();
    var batchedReadStream = new BatchedReadStream<>(delegate);

    batchedReadStream.exceptionHandler(error -> {});

    verify(delegate, times(1)).exceptionHandler(any());
  }

  @Test
  public void shouldDelegateCallForEndHandler() {
    var delegate = mockStream();
    var batchedReadStream = new BatchedReadStream<>(delegate);

    batchedReadStream.endHandler(error -> {});

    verify(delegate, times(1)).endHandler(any());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldProcessRemainingRecordsWhenEndReached() {
    var batchSize = 10;
    var delegate = new ReindexJobRunnerTest.TestRowStream(21);
    var batchedReadStream = new BatchedReadStream<>(delegate, batchSize);
    Handler<Void> endHandler = mock(Handler.class);
    Handler<List<Row>> handler = mock(Handler.class);

    batchedReadStream
      .handler(handler)
      .endHandler(endHandler);

    await().untilAsserted(() -> {
      var captor = ArgumentCaptor.forClass(List.class);
      verify(endHandler, times(1)).handle(any());

      verify(handler, times(3)).handle(captor.capture());
      assertThat(captor.getAllValues()).hasSize(3);
      assertThat(captor.getAllValues().get(0)).hasSize(batchSize);
      assertThat(captor.getAllValues().get(1)).hasSize(batchSize);
      assertThat(captor.getAllValues().get(2)).hasSize(1);
    });
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldHandleErrorWhenOccurredOnRemainingChunk() {
    var delegate = new ReindexJobRunnerTest.TestRowStream(1);
    var batchedReadStream = new BatchedReadStream<>(delegate);
    Handler<Void> endHandler = mock(Handler.class);
    Handler<List<Row>> handler = mock(Handler.class);
    Handler<Throwable> exceptionHandler = mock(Handler.class);

    doThrow(new RuntimeException()).when(handler).handle(any());

    batchedReadStream
      .exceptionHandler(exceptionHandler)
      .handler(handler)
      .endHandler(endHandler);

    await().untilAsserted(() -> {
      verifyNoInteractions(endHandler);
      verify(handler, times(1)).handle(any());
      verify(exceptionHandler, times(1)).handle(any());
    });
  }

  @Test
  public void shouldDelegateCallForPause() {
    var delegate = mockStream();
    var batchedReadStream = new BatchedReadStream<>(delegate);

    batchedReadStream.pause();

    verify(delegate, times(1)).pause();
  }

  @Test
  public void shouldDelegateCallForResume() {
    var delegate = mockStream();
    var batchedReadStream = new BatchedReadStream<>(delegate);

    batchedReadStream.resume();

    verify(delegate, times(1)).resume();
  }

  @Test
  public void shouldDelegateCallForFetch() {
    var delegate = mockStream();
    var batchedReadStream = new BatchedReadStream<>(delegate);

    batchedReadStream.fetch(10);

    verify(delegate, times(1)).fetch(10);
  }

  @SuppressWarnings("unchecked")
  private ReadStream<Integer> mockStream() {
    return mock(ReadStream.class);
  }
}
