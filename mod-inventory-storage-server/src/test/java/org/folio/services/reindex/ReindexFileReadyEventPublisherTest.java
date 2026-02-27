package org.folio.services.reindex;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.folio.okapi.common.XOkapiHeaders.TENANT;
import static org.folio.rest.api.TestBase.get;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.kafka.client.producer.KafkaProducer;
import java.util.Map;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.folio.kafka.KafkaProducerManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ReindexFileReadyEventPublisherTest {

  private static final String TENANT_ID = "test-tenant";

  @Mock
  private KafkaProducerManager producerManager;
  @Mock
  private KafkaProducer<Object, Object> producer;

  @Test
  public void publish_success_eventSentToKafka() {
    final var publisher = new ReindexFileReadyEventPublisher(okapiHeaders(), producerManager);
    when(producerManager.createShared(any())).thenReturn(producer);
    when(producer.send(any())).thenReturn(succeededFuture());
    when(producer.flush()).thenReturn(succeededFuture());
    when(producer.close()).thenReturn(succeededFuture());

    get(publisher.publish(buildEvent()));

    verify(producer).send(any());
  }

  @Test
  public void publish_kafkaSendFails_futureFailedWithCause() {
    final var publisher = new ReindexFileReadyEventPublisher(okapiHeaders(), producerManager);
    var cause = new RuntimeException("Kafka down");
    when(producerManager.createShared(any())).thenReturn(producer);
    when(producer.send(any())).thenReturn(failedFuture(cause));
    when(producer.flush()).thenReturn(succeededFuture());
    when(producer.close()).thenReturn(succeededFuture());

    var publishResult = publisher.publish(buildEvent());
    assertThrows(RuntimeException.class, () -> get(publishResult));
  }

  private static Map<String, String> okapiHeaders() {
    return new CaseInsensitiveMap<>(Map.of(TENANT, TENANT_ID));
  }

  private static ReindexFileReadyEvent buildEvent() {
    return ReindexFileReadyEvent.builder()
      .tenantId(TENANT_ID)
      .recordType("instance")
      .range("00000000-0000-0000-0000-000000000000", "ffffffff-ffff-ffff-ffff-ffffffffffff")
      .rangeId("range-id-1")
      .traceId("job-id-1")
      .bucket("test-bucket")
      .objectKey("test-tenant/instance/job-id-1/range-id-1.ndjson")
      .build();
  }
}
