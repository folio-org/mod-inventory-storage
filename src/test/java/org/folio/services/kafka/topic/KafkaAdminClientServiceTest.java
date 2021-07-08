package org.folio.services.kafka.topic;

import static io.vertx.core.Future.succeededFuture;
import static java.util.Set.of;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import io.vertx.core.Future;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.kafka.admin.KafkaAdminClient;
import io.vertx.kafka.admin.NewTopic;

@RunWith(VertxUnitRunner.class)
public class KafkaAdminClientServiceTest {
  private final Set<String> allTopics = Stream.of(KafkaTopic.values())
    .map(KafkaTopic::getTopicName).collect(Collectors.toSet());

  @Test
  public void shouldNotCreateTopicIfAlreadyExist(TestContext testContext) {
    final KafkaAdminClient mockClient = mock(KafkaAdminClient.class);
    when(mockClient.listTopics()).thenReturn(succeededFuture(allTopics));
    when(mockClient.close()).thenReturn(succeededFuture());

    createKafkaTopicsAsync(mockClient)
      .onFailure(testContext::fail)
      .onComplete(testContext.asyncAssertSuccess(notUsed -> {
        verify(mockClient, times(0)).createTopics(anyList());
        verify(mockClient, times(1)).close();
      }));
  }

  @Test
  public void shouldCreateTopicIfNotExist(TestContext testContext) {
    final KafkaAdminClient mockClient = mock(KafkaAdminClient.class);
    when(mockClient.listTopics()).thenReturn(succeededFuture(of("inventory.some-another-topic")));
    when(mockClient.createTopics(anyList())).thenReturn(succeededFuture());
    when(mockClient.close()).thenReturn(succeededFuture());

    createKafkaTopicsAsync(mockClient)
      .onFailure(testContext::fail)
      .onComplete(testContext.asyncAssertSuccess(notUsed -> {

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<List<NewTopic>> createTopicsCaptor = forClass(List.class);

        verify(mockClient, times(1)).createTopics(createTopicsCaptor.capture());
        verify(mockClient, times(1)).close();

        testContext.assertEquals(1, createTopicsCaptor.getAllValues().size());
        testContext.assertEquals(allTopics.size(), createTopicsCaptor.getAllValues().get(0).size());
        testContext.assertEquals("inventory.instance", createTopicsCaptor.getAllValues().get(0).get(0).getName());
        testContext.assertEquals("inventory.item", createTopicsCaptor.getAllValues().get(0).get(1).getName());
        testContext.assertEquals("inventory.holdings-record", createTopicsCaptor.getAllValues().get(0).get(2).getName());
      }));
  }

  private Future<Void> createKafkaTopicsAsync(KafkaAdminClient mockClient) {
    return new KafkaAdminClientService(() -> mockClient).createKafkaTopics();
  }
}
