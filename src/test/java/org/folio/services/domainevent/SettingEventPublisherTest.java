package org.folio.services.domainevent;

import static org.folio.okapi.common.XOkapiHeaders.TENANT;
import static org.folio.okapi.common.XOkapiHeaders.USER_ID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import io.vertx.kafka.client.producer.RecordMetadata;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.folio.kafka.KafkaProducerManager;
import org.folio.kafka.services.KafkaEnvironmentProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class SettingEventPublisherTest {

  private static final String TENANT_ID = "test-tenant";
  private static final String SETTING_KEY = "inventory.optimize-updates.enabled";
  private static final String SETTING_VALUE = "true";

  private KafkaProducerManager producerManager;
  private KafkaProducer<String, String> kafkaProducer;
  private MockedStatic<KafkaEnvironmentProperties> mockedKafkaEnvProperties;
  private Map<String, String> okapiHeaders;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    producerManager = mock(KafkaProducerManager.class);
    kafkaProducer = mock(KafkaProducer.class);

    when(producerManager.<String, String>createShared(anyString())).thenReturn(kafkaProducer);

    mockedKafkaEnvProperties = mockStatic(KafkaEnvironmentProperties.class);
    mockedKafkaEnvProperties.when(KafkaEnvironmentProperties::host).thenReturn("localhost");
    mockedKafkaEnvProperties.when(KafkaEnvironmentProperties::port).thenReturn("9092");

    okapiHeaders = new HashMap<>();
    okapiHeaders.put(TENANT, TENANT_ID);
    okapiHeaders.put(USER_ID, "00000000-0000-0000-0000-000000000000");
  }

  @AfterEach
  void tearDown() {
    mockedKafkaEnvProperties.close();
  }

  @Test
  void constructorWithProducerManagerShouldCreatePublisher() {
    var publisher = new SettingEventPublisher(producerManager);

    assertThat(publisher, is(notNullValue()));
  }

  @Test
  @SuppressWarnings("unchecked")
  void publishShouldSucceedWhenKafkaProducerSendsSuccessfully() {
    var eventId = UUID.randomUUID().toString();
    var event = new SettingEvent(eventId, SETTING_KEY, SETTING_VALUE, TENANT_ID);
    var publisher = new SettingEventPublisher(producerManager);

    var recordMetadata = mock(RecordMetadata.class);
    when(kafkaProducer.send(any(KafkaProducerRecord.class)))
      .thenReturn(Future.succeededFuture(recordMetadata));
    when(kafkaProducer.flush()).thenReturn(Future.succeededFuture());
    when(kafkaProducer.close()).thenReturn(Future.succeededFuture());

    var result = publisher.publish(event, eventId, okapiHeaders);

    assertThat(result.succeeded(), is(true));
    verify(producerManager).createShared(anyString());
    verify(kafkaProducer).send(any(KafkaProducerRecord.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  void publishShouldFailWhenKafkaProducerSendFails() {
    var eventId = UUID.randomUUID().toString();
    var event = new SettingEvent(eventId, SETTING_KEY, SETTING_VALUE, TENANT_ID);
    var publisher = new SettingEventPublisher(producerManager);

    when(kafkaProducer.send(any(KafkaProducerRecord.class)))
      .thenReturn(Future.failedFuture(new RuntimeException("Kafka send failed")));
    when(kafkaProducer.flush()).thenReturn(Future.succeededFuture());
    when(kafkaProducer.close()).thenReturn(Future.succeededFuture());

    var result = publisher.publish(event, eventId, okapiHeaders);

    assertThat(result.failed(), is(true));
    assertThat(result.cause().getMessage(), is("Kafka send failed"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void publishShouldUseCorrectTopic() {
    var recordMetadata = mock(RecordMetadata.class);
    when(kafkaProducer.send(any(KafkaProducerRecord.class)))
      .thenReturn(Future.succeededFuture(recordMetadata));
    when(kafkaProducer.flush()).thenReturn(Future.succeededFuture());
    when(kafkaProducer.close()).thenReturn(Future.succeededFuture());
    var eventId = UUID.randomUUID().toString();
    var event = new SettingEvent(eventId, SETTING_KEY, SETTING_VALUE, TENANT_ID);
    var publisher = new SettingEventPublisher(producerManager);

    publisher.publish(event, eventId, okapiHeaders);

    verify(producerManager).createShared(anyString());
  }

  @Test
  @SuppressWarnings("unchecked")
  void publishShouldFlushProducerAfterSend() {
    var recordMetadata = mock(RecordMetadata.class);
    when(kafkaProducer.send(any(KafkaProducerRecord.class)))
      .thenReturn(Future.succeededFuture(recordMetadata));
    when(kafkaProducer.flush()).thenReturn(Future.succeededFuture());
    when(kafkaProducer.close()).thenReturn(Future.succeededFuture());
    var eventId = UUID.randomUUID().toString();
    var event = new SettingEvent(eventId, SETTING_KEY, SETTING_VALUE, TENANT_ID);
    var publisher = new SettingEventPublisher(producerManager);

    publisher.publish(event, eventId, okapiHeaders);

    verify(kafkaProducer).flush();
  }

  @Test
  @SuppressWarnings("unchecked")
  void publishShouldCloseProducerAfterFlush() {
    var recordMetadata = mock(RecordMetadata.class);
    when(kafkaProducer.send(any(KafkaProducerRecord.class)))
      .thenReturn(Future.succeededFuture(recordMetadata));
    when(kafkaProducer.flush()).thenReturn(Future.succeededFuture());
    when(kafkaProducer.close()).thenReturn(Future.succeededFuture());
    var publisher = new SettingEventPublisher(producerManager);
    var eventId = UUID.randomUUID().toString();
    var event = new SettingEvent(eventId, SETTING_KEY, SETTING_VALUE, TENANT_ID);

    publisher.publish(event, eventId, okapiHeaders);

    verify(kafkaProducer).close();
  }

  @Test
  @SuppressWarnings("unchecked")
  void publishShouldHandleEventWithBooleanValue() {
    var eventId = UUID.randomUUID().toString();
    var event = new SettingEvent(eventId, SETTING_KEY, true, TENANT_ID);
    var publisher = new SettingEventPublisher(producerManager);

    var recordMetadata = mock(RecordMetadata.class);
    when(kafkaProducer.send(any(KafkaProducerRecord.class)))
      .thenReturn(Future.succeededFuture(recordMetadata));
    when(kafkaProducer.flush()).thenReturn(Future.succeededFuture());
    when(kafkaProducer.close()).thenReturn(Future.succeededFuture());

    var result = publisher.publish(event, eventId, okapiHeaders);

    assertThat(result.succeeded(), is(true));
  }

  @Test
  @SuppressWarnings("unchecked")
  void publishShouldHandleEventWithIntegerValue() {
    var eventId = UUID.randomUUID().toString();
    var event = new SettingEvent(eventId, "some.integer.setting", 42, TENANT_ID);
    var publisher = new SettingEventPublisher(producerManager);

    var recordMetadata = mock(RecordMetadata.class);
    when(kafkaProducer.send(any(KafkaProducerRecord.class)))
      .thenReturn(Future.succeededFuture(recordMetadata));
    when(kafkaProducer.flush()).thenReturn(Future.succeededFuture());
    when(kafkaProducer.close()).thenReturn(Future.succeededFuture());

    var result = publisher.publish(event, eventId, okapiHeaders);

    assertThat(result.succeeded(), is(true));
  }

  @Test
  @SuppressWarnings("unchecked")
  void publishShouldHandleEventWithStringValue() {
    var eventId = UUID.randomUUID().toString();
    var event = new SettingEvent(eventId, "some.string.setting", "string-value", TENANT_ID);
    var publisher = new SettingEventPublisher(producerManager);

    var recordMetadata = mock(RecordMetadata.class);
    when(kafkaProducer.send(any(KafkaProducerRecord.class)))
      .thenReturn(Future.succeededFuture(recordMetadata));
    when(kafkaProducer.flush()).thenReturn(Future.succeededFuture());
    when(kafkaProducer.close()).thenReturn(Future.succeededFuture());

    var result = publisher.publish(event, eventId, okapiHeaders);

    assertThat(result.succeeded(), is(true));
  }

  @Test
  @SuppressWarnings("unchecked")
  void publishShouldPropagateOkapiHeaders() {
    var eventId = UUID.randomUUID().toString();
    var event = new SettingEvent(eventId, SETTING_KEY, SETTING_VALUE, TENANT_ID);
    var publisher = new SettingEventPublisher(producerManager);

    var recordMetadata = mock(RecordMetadata.class);
    when(kafkaProducer.send(any(KafkaProducerRecord.class)))
      .thenReturn(Future.succeededFuture(recordMetadata));
    when(kafkaProducer.flush()).thenReturn(Future.succeededFuture());
    when(kafkaProducer.close()).thenReturn(Future.succeededFuture());

    var result = publisher.publish(event, eventId, okapiHeaders);

    assertThat(result.succeeded(), is(true));
    verify(kafkaProducer).send(any(KafkaProducerRecord.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  void publishShouldCompleteSuccessfullyEvenWhenFlushFails() {
    var eventId = UUID.randomUUID().toString();
    var event = new SettingEvent(eventId, SETTING_KEY, SETTING_VALUE, TENANT_ID);
    var publisher = new SettingEventPublisher(producerManager);

    var recordMetadata = mock(RecordMetadata.class);
    when(kafkaProducer.send(any(KafkaProducerRecord.class)))
      .thenReturn(Future.succeededFuture(recordMetadata));
    when(kafkaProducer.flush()).thenReturn(Future.failedFuture(new RuntimeException("Flush failed")));
    when(kafkaProducer.close()).thenReturn(Future.succeededFuture());

    var result = publisher.publish(event, eventId, okapiHeaders);

    assertThat(result.succeeded(), is(true));
  }
}
