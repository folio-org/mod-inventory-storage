package org.folio.services.kafka.topic;

import static io.vertx.core.Future.succeededFuture;
import static java.util.Set.of;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Test;

import io.vertx.core.Vertx;
import io.vertx.kafka.admin.KafkaAdminClient;

public class KafkaAdminClientServiceTest {
  private KafkaAdminClientService adminClientService;

  @Before
  public void createAdminClientService() {
    adminClientService = spy(new KafkaAdminClientService(Vertx.vertx()));
  }

  @Test
  public void shouldNotCreateTopicIfAlreadyExist() {
    final KafkaAdminClient mockClient = mock(KafkaAdminClient.class);
    when(mockClient.listTopics()).thenReturn(succeededFuture(of("inventory.instance")));
    when(mockClient.close()).thenReturn(succeededFuture());

    createKafkaTopicsAsync(mockClient);

    verify(mockClient, times(0)).createTopics(anyList());
    verify(mockClient, times(1)).close();
  }

  @Test
  public void shouldCreateTopicIfNotExist() {
    final KafkaAdminClient mockClient = mock(KafkaAdminClient.class);
    when(mockClient.listTopics()).thenReturn(succeededFuture(of("inventory.some-another-topic")));
    when(mockClient.createTopics(anyList())).thenReturn(succeededFuture());
    when(mockClient.close()).thenReturn(succeededFuture());

    createKafkaTopicsAsync(mockClient);

    verify(mockClient, times(1)).createTopics(anyList());
    verify(mockClient, times(1)).close();
  }

  private void createKafkaTopicsAsync(KafkaAdminClient mockClient) {
    when(adminClientService.createKafkaAdminNativeClient())
      .thenReturn(mockClient);

    adminClientService.createKafkaTopicsAsync();

    Awaitility.await()
      .atLeast(100, TimeUnit.MILLISECONDS);
  }
}
