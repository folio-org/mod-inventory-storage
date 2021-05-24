package org.folio.services.domainevent;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.folio.rest.api.TestBase.get;
import static org.folio.services.kafka.topic.KafkaTopic.INVENTORY_INSTANCE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Handler;
import io.vertx.kafka.client.producer.KafkaProducer;
import java.util.Map;
import org.folio.kafka.KafkaProducerManager;
import org.folio.rest.api.ReindexJobRunnerTest;
import org.folio.rest.api.entities.Instance;
import org.folio.services.kafka.InventoryProducerRecordBuilder;
import org.folio.services.kafka.topic.KafkaTopic;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CommonDomainEventPublisherTest {
  @Mock private KafkaProducer<String, String> producer;
  @Mock private KafkaProducerManager producerManager;
  @Mock private FailureHandler failureHandler;
  private CommonDomainEventPublisher<Instance> eventPublisher;

  @Before
  public void setUpPublisher() {
    eventPublisher = new CommonDomainEventPublisher<>(
      Map.of(), INVENTORY_INSTANCE, producerManager, failureHandler);
  }

  @Test
  public void shouldPauseStreamWhenProducerIsFull() {
    var stream = spy(new ReindexJobRunnerTest.TestRowStream(6));

    when(producerManager.<String, String>createShared(any())).thenReturn(producer);
    when(producer.writeQueueFull()).thenReturn(false, false, false, true, false, true);
    when(producer.send(any())).thenReturn(succeededFuture());
    when(producer.drainHandler(any())).thenAnswer(this::drainHandler);

    var recordsPublished = get(eventPublisher.publishStream(stream,
      row -> new InventoryProducerRecordBuilder(), notUsed -> succeededFuture()));

    assertThat(recordsPublished).isEqualTo(6);

    verify(producer, times(6)).send(any());
    verify(stream, times(2)).pause();
    verify(stream, times(2)).resume();
  }

  @Test
  public void shouldStopProcessingIfProgressThrowsError() {
    var stream = spy(new ReindexJobRunnerTest.TestRowStream(6));

    when(producerManager.<String, String>createShared(any())).thenReturn(producer);
    when(producer.send(any())).thenReturn(succeededFuture());

    var future = eventPublisher.publishStream(stream,
      row -> new InventoryProducerRecordBuilder(),
      records -> records > 3 ? failedFuture("stream failed") : succeededFuture());

    await().until(future::isComplete);

    assertThat(future.failed()).isTrue();
    assertThat(future.cause()).hasMessage("stream failed");

    verify(producer, times(4)).send(any());
    verify(stream, times(1)).pause();
    verify(stream, times(0)).resume();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldAdjustRecordCountWhenSomeFailed() {
    var stream = spy(new ReindexJobRunnerTest.TestRowStream(4));

    when(producerManager.<String, String>createShared(any())).thenReturn(producer);
    when(producer.send(any()))
      .thenReturn(succeededFuture(), failedFuture(""), succeededFuture(), failedFuture(""));

    var recordsPublished = get(eventPublisher.publishStream(stream,
      row -> new InventoryProducerRecordBuilder(), records -> succeededFuture()));

    assertThat(recordsPublished).isEqualTo(2);
    verify(failureHandler, times(2)).handleFailure(any(), any());
  }

  @Test
  public void shouldStopProcessingIfErrorOccurred() {
    var stream = spy(new ReindexJobRunnerTest.TestRowStream(4));

    when(producerManager.<String, String>createShared(any())).thenReturn(producer);
    when(producer.send(any())).thenThrow(new IllegalStateException("server error"));

    var future = eventPublisher.publishStream(stream,
      row -> new InventoryProducerRecordBuilder(), records -> succeededFuture());

    await().until(future::isComplete);

    assertThat(future.failed()).isTrue();
    assertThat(future.cause()).hasMessage("server error");

    verify(producer, times(1)).send(any());
  }

  @Test
  public void shouldCallFailureHandlerWhenPublishingFailed() {
    var causeError = new IllegalArgumentException("error");

    when(producerManager.<String, String>createShared(any())).thenReturn(producer);
    when(producer.send(any())).thenReturn(failedFuture(causeError));

    assertThatThrownBy(() -> get(eventPublisher.publishAllRecordsRemoved()))
      .hasRootCauseInstanceOf(IllegalArgumentException.class);

    verify(failureHandler, times(1)).handleFailure(eq(causeError), any());
  }

  @SuppressWarnings("unchecked")
  private Void drainHandler(InvocationOnMock invocationOnMock) {
    invocationOnMock.getArgument(0, Handler.class)
      .handle(null);

    return null;
  }
}
