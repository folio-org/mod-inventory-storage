package org.folio.services.domainevent;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.awaitility.Awaitility.await;
import static org.folio.InventoryKafkaTopic.INSTANCE;
import static org.folio.rest.api.TestBase.get;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Handler;
import io.vertx.kafka.client.producer.KafkaProducer;
import java.util.Map;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.folio.kafka.KafkaProducerManager;
import org.folio.kafka.services.KafkaProducerRecordBuilder;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.support.sql.TestRowStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@MockitoSettings(strictness = Strictness.WARN)
@ExtendWith(MockitoExtension.class)
class CommonDomainEventPublisherTest {
  private static final String TENANT_ID = "foo";

  @Mock
  private KafkaProducer<String, String> producer;
  @Mock
  private KafkaProducerManager producerManager;
  @Mock
  private FailureHandler failureHandler;
  private CommonDomainEventPublisher<Instance> eventPublisher;

  @BeforeEach
  void setUpPublisher() {
    eventPublisher = new CommonDomainEventPublisher<>(
      new CaseInsensitiveMap<>(Map.of()), INSTANCE.fullTopicName(TENANT_ID),
      producerManager, failureHandler);
  }

  @Test
  void shouldPauseStreamWhenProducerIsFull() {
    var stream = spy(new TestRowStream(6));

    when(producerManager.<String, String>createShared(any())).thenReturn(producer);
    when(producer.writeQueueFull()).thenReturn(false, false, false, true, false, true);
    when(producer.send(any())).thenReturn(succeededFuture());
    when(producer.drainHandler(any())).thenAnswer(this::drainHandler);

    var recordsPublished = get(eventPublisher.publishStream(stream,
      row -> builderWithValue(""), notUsed -> succeededFuture()));

    assertThat(recordsPublished, is(6L));

    verify(producer, times(6)).send(any());
    verify(stream, times(2)).pause();
    verify(stream, times(2)).resume();
  }

  @Test
  void shouldStopProcessingIfProgressThrowsError() {
    var stream = spy(new TestRowStream(6));

    when(producerManager.<String, String>createShared(any())).thenReturn(producer);
    when(producer.send(any())).thenReturn(succeededFuture());

    var future = eventPublisher.publishStream(stream,
      row -> builderWithValue(""),
      records -> records > 3 ? failedFuture("stream failed") : succeededFuture());

    await().until(future::isComplete);

    assertThat(future.failed(), is(true));
    assertThat(future.cause().getMessage(), is("stream failed"));

    verify(producer, times(4)).send(any());
    verify(stream, times(1)).pause();
    verify(stream, times(0)).resume();
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldAdjustRecordCountWhenSomeFailed() {
    var stream = spy(new TestRowStream(4));

    when(producerManager.<String, String>createShared(any())).thenReturn(producer);
    when(producer.send(any()))
      .thenReturn(succeededFuture(), failedFuture(""), succeededFuture(), failedFuture(""));

    var recordsPublished = get(eventPublisher.publishStream(stream,
      row -> builderWithValue(""), records -> succeededFuture()));

    assertThat(recordsPublished, is(2L));
    verify(failureHandler, times(2)).handleFailure(any(), any());
  }

  @Test
  void shouldStopProcessingIfErrorOccurred() {
    var stream = spy(new TestRowStream(4));

    when(producerManager.<String, String>createShared(any())).thenReturn(producer);
    when(producer.send(any())).thenThrow(new IllegalStateException("server error"));

    var future = eventPublisher.publishStream(stream,
      row -> builderWithValue(""), records -> succeededFuture());

    await().until(future::isComplete);

    assertThat(future.failed(), is(true));
    assertThat(future.cause().getMessage(), is("server error"));

    verify(producer, times(1)).send(any());
  }

  @Test
  void shouldCallFailureHandlerWhenPublishingFailed() {
    var causeError = new IllegalArgumentException("error");

    when(producerManager.<String, String>createShared(any())).thenReturn(producer);
    when(producer.close()).thenReturn(succeededFuture());
    when(producer.flush()).thenReturn(succeededFuture());
    when(producer.send(any())).thenReturn(failedFuture(causeError));

    var future = eventPublisher.publishAllRecordsRemoved();
    var e = assertThrows(RuntimeException.class, () -> get(future));
    assertInstanceOf(IllegalArgumentException.class, e.getCause().getCause());

    verify(failureHandler, times(1)).handleFailure(eq(causeError), any());
  }

  @SuppressWarnings("unchecked")
  private Void drainHandler(InvocationOnMock invocationOnMock) {
    invocationOnMock.getArgument(0, Handler.class)
      .handle(null);

    return null;
  }

  private KafkaProducerRecordBuilder<String, Object> builderWithValue(Object value) {
    return new KafkaProducerRecordBuilder<String, Object>(TENANT_ID).value(value);
  }
}
