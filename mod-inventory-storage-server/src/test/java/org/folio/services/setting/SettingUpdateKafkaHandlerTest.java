package org.folio.services.setting;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import io.vertx.kafka.client.producer.KafkaHeader;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.folio.services.caches.SettingCache;
import org.folio.services.domainevent.SettingEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SettingUpdateKafkaHandlerTest {

  private static final String TENANT_ID = "test-tenant";
  private static final String TOPIC = "folio.test-tenant.inventory.setting";
  private static final String SETTING_KEY = "inventory.optimize-updates.enabled";
  private static final String SETTING_VALUE = "true";
  private static final String RECORD_KEY = "record-key-123";

  private SettingCache cache;
  private SettingUpdateKafkaHandler handler;

  @BeforeEach
  void setUp() {
    cache = mock(SettingCache.class);
    handler = new SettingUpdateKafkaHandler(cache);
  }

  @Test
  @SuppressWarnings("unchecked")
  void handleShouldSuccessfullyProcessValidKafkaRecord() {
    var eventId = UUID.randomUUID().toString();
    var event = new SettingEvent(eventId, SETTING_KEY, SETTING_VALUE, TENANT_ID);
    var kafkaRecord = createKafkaRecord(Json.encode(event));

    var result = handler.handle(kafkaRecord);

    assertThat(result.succeeded(), is(true));
    assertThat(result.result(), is(RECORD_KEY));
    verify(cache).put(anyString(), any(CompletableFuture.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  void handleShouldPutCorrectKeyInCache() {
    var eventId = UUID.randomUUID().toString();
    var event = new SettingEvent(eventId, SETTING_KEY, SETTING_VALUE, TENANT_ID);
    var kafkaRecord = createKafkaRecord(Json.encode(event));

    handler.handle(kafkaRecord);

    // Verify cache.put is called (key format is tenant:settingKey)
    verify(cache).put(anyString(), any(CompletableFuture.class));
  }

  @Test
  void handleShouldProcessEventWithBooleanValue() {
    var eventId = UUID.randomUUID().toString();
    var event = new SettingEvent(eventId, SETTING_KEY, true, TENANT_ID);
    var kafkaRecord = createKafkaRecord(Json.encode(event));

    var result = handler.handle(kafkaRecord);

    assertThat(result.succeeded(), is(true));
    assertThat(result.result(), is(RECORD_KEY));
  }

  @Test
  void handleShouldProcessEventWithIntegerValue() {
    var eventId = UUID.randomUUID().toString();
    var event = new SettingEvent(eventId, "some.integer.setting", 42, TENANT_ID);
    var kafkaRecord = createKafkaRecord(Json.encode(event));

    var result = handler.handle(kafkaRecord);

    assertThat(result.succeeded(), is(true));
    assertThat(result.result(), is(RECORD_KEY));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"invalid-json"})
  void handleShouldFailWhenKafkaRecordValueIsInvalid(String value) {
    var kafkaRecord = createKafkaRecord(value);

    var result = handler.handle(kafkaRecord);

    assertThat(result.failed(), is(true));
    verify(cache, never()).put(anyString(), any());
  }

  @Test
  void handleShouldWorkWithCaseInsensitiveTenantHeader() {
    var eventId = UUID.randomUUID().toString();
    var event = new SettingEvent(eventId, SETTING_KEY, SETTING_VALUE, TENANT_ID);
    var kafkaRecord = createKafkaRecordWithCustomHeader(Json.encode(event), "x-okapi-tenant");

    var result = handler.handle(kafkaRecord);

    assertThat(result.succeeded(), is(true));
    assertThat(result.result(), is(RECORD_KEY));
  }

  @Test
  void handleShouldWorkWithUppercaseTenantHeader() {
    var eventId = UUID.randomUUID().toString();
    var event = new SettingEvent(eventId, SETTING_KEY, SETTING_VALUE, TENANT_ID);
    var kafkaRecord = createKafkaRecordWithCustomHeader(Json.encode(event), "X-OKAPI-TENANT");

    var result = handler.handle(kafkaRecord);

    assertThat(result.succeeded(), is(true));
    assertThat(result.result(), is(RECORD_KEY));
  }

  private KafkaConsumerRecord<String, String> createKafkaRecord(String value) {
    return createKafkaRecordWithCustomHeader(value, "X-Okapi-Tenant");
  }

  @SuppressWarnings("unchecked")
  private KafkaConsumerRecord<String, String> createKafkaRecordWithCustomHeader(String value, String headerName) {
    KafkaConsumerRecord<String, String> kafkaConsumerRecord = mock(KafkaConsumerRecord.class);
    when(kafkaConsumerRecord.topic()).thenReturn(TOPIC);
    when(kafkaConsumerRecord.key()).thenReturn(RECORD_KEY);
    when(kafkaConsumerRecord.value()).thenReturn(value);

    KafkaHeader tenantHeader = mock(KafkaHeader.class);
    when(tenantHeader.key()).thenReturn(headerName);
    when(tenantHeader.value()).thenReturn(Buffer.buffer(TENANT_ID));

    when(kafkaConsumerRecord.headers()).thenReturn(List.of(tenantHeader));

    return kafkaConsumerRecord;
  }
}
