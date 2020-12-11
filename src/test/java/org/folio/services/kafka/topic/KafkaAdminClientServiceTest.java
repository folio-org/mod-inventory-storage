package org.folio.services.kafka.topic;

import static io.vertx.core.Future.succeededFuture;
import static java.util.Set.of;
import static org.folio.services.kafka.topic.KafkaAdminClientService.createKafkaTopicsAsync;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.kafka.admin.KafkaAdminClient;

@RunWith(VertxUnitRunner.class)
public class KafkaAdminClientServiceTest {
  @Test
  public void shouldNotCreateTopicIfAlreadyExist(TestContext testContext) {
    final KafkaAdminClient mockClient = mock(KafkaAdminClient.class);
    when(mockClient.listTopics()).thenReturn(succeededFuture(of("inventory.instance")));

    createKafkaTopicsAsync(mockClient).onComplete(testContext.asyncAssertSuccess(
      notUsed -> verify(mockClient, times(0)).createTopics(anyList())));
  }

  @Test
  public void shouldCreateTopicIfNotExist(TestContext testContext) {
    final KafkaAdminClient mockClient = mock(KafkaAdminClient.class);
    when(mockClient.listTopics()).thenReturn(succeededFuture(of("inventory.some-another-topic")));
    when(mockClient.createTopics(anyList())).thenReturn(succeededFuture());

    createKafkaTopicsAsync(mockClient).onComplete(testContext.asyncAssertSuccess(
      notUsed -> verify(mockClient, times(1)).createTopics(anyList())));
  }
}
