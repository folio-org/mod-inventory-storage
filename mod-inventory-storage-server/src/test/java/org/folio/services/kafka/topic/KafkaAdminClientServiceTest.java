package org.folio.services.kafka.topic;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.kafka.admin.KafkaAdminClient;
import io.vertx.kafka.admin.NewTopic;
import java.util.List;
import java.util.Set;
import org.apache.kafka.common.errors.TopicExistsException;
import org.folio.InventoryKafkaTopic;
import org.folio.kafka.services.KafkaAdminClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

@ExtendWith(VertxExtension.class)
class KafkaAdminClientServiceTest {
  private static final String STUB_TENANT = "foo-tenant";

  private final Set<String> allExpectedTopics = Set.of(
    "folio.foo-tenant.inventory.instance",
    "folio.foo-tenant.inventory.holdings-record",
    "folio.foo-tenant.inventory.item",
    "folio.foo-tenant.inventory.instance-contribution",
    "folio.foo-tenant.inventory.bound-with",
    "folio.foo-tenant.inventory.async-migration",
    "folio.foo-tenant.inventory.service-point",
    "folio.foo-tenant.inventory.material-type",
    "folio.foo-tenant.inventory.classification-type",
    "folio.foo-tenant.inventory.location",
    "folio.foo-tenant.inventory.library",
    "folio.foo-tenant.inventory.campus",
    "folio.foo-tenant.inventory.loan-type",
    "folio.foo-tenant.inventory.subject-type",
    "folio.foo-tenant.inventory.institution",
    "folio.foo-tenant.inventory.reindex-records",
    "folio.foo-tenant.inventory.reindex.file-ready",
    "folio.foo-tenant.inventory.subject-source",
    "folio.foo-tenant.inventory.instance-date-type",
    "folio.foo-tenant.inventory.call-number-type",
    "folio.foo-tenant.inventory.setting");
  private KafkaAdminClient mockClient;
  private Vertx vertx;

  @BeforeEach
  void setUp() {
    vertx = mock(Vertx.class);
    mockClient = mock(KafkaAdminClient.class);
  }

  @Test
  void shouldCreateTopicIfAlreadyExist(VertxTestContext testContext) {
    when(mockClient.createTopics(anyList()))
      .thenReturn(failedFuture(new TopicExistsException("x")))
      .thenReturn(failedFuture(new TopicExistsException("y")))
      .thenReturn(failedFuture(new TopicExistsException("z")))
      .thenReturn(succeededFuture());
    when(mockClient.listTopics()).thenReturn(succeededFuture(Set.of("old")));
    when(mockClient.close()).thenReturn(succeededFuture());

    createKafkaTopicsAsync(mockClient)
      .onComplete(testContext.succeeding(notUsed -> {
        testContext.verify(() -> {
          verify(mockClient, times(4)).listTopics();
          verify(mockClient, times(4)).createTopics(anyList());
          verify(mockClient, times(1)).close();
        });
        testContext.completeNow();
      }));
  }

  @Test
  void shouldFailIfExistExceptionIsPermanent(VertxTestContext testContext) {
    when(mockClient.createTopics(anyList())).thenReturn(failedFuture(new TopicExistsException("x")));
    when(mockClient.listTopics()).thenReturn(succeededFuture(Set.of("old")));
    when(mockClient.close()).thenReturn(succeededFuture());

    createKafkaTopicsAsync(mockClient)
      .onComplete(testContext.failing(e -> {
        testContext.verify(() -> {
          assertInstanceOf(TopicExistsException.class, e);
          verify(mockClient, times(1)).close();
        });
        testContext.completeNow();
      }));
  }

  @Test
  void shouldNotCreateTopicOnOther(VertxTestContext testContext) {
    when(mockClient.createTopics(anyList())).thenReturn(failedFuture(new RuntimeException("err msg")));
    when(mockClient.listTopics()).thenReturn(succeededFuture(Set.of("old")));
    when(mockClient.close()).thenReturn(succeededFuture());

    createKafkaTopicsAsync(mockClient)
      .onComplete(testContext.failing(cause -> {
        testContext.verify(() -> {
          assertEquals("err msg", cause.getMessage());
          verify(mockClient, times(1)).close();
        });
        testContext.completeNow();
      }));
  }

  @Test
  void shouldCreateTopicIfNotExist(VertxTestContext testContext) {
    when(mockClient.createTopics(anyList())).thenReturn(succeededFuture());
    when(mockClient.listTopics()).thenReturn(succeededFuture(Set.of("old")));
    when(mockClient.close()).thenReturn(succeededFuture());

    createKafkaTopicsAsync(mockClient)
      .onComplete(testContext.succeeding(notUsed -> {
        testContext.verify(() -> {
          @SuppressWarnings("unchecked") final ArgumentCaptor<List<NewTopic>> createTopicsCaptor = forClass(List.class);

          verify(mockClient, times(1)).createTopics(createTopicsCaptor.capture());
          verify(mockClient, times(1)).close();

          // Only these items are expected, so implicitly checks size of list
          assertThat(getTopicNames(createTopicsCaptor), containsInAnyOrder(allExpectedTopics.toArray()));
        });
        testContext.completeNow();
      }));
  }

  private List<String> getTopicNames(ArgumentCaptor<List<NewTopic>> createTopicsCaptor) {
    return createTopicsCaptor.getAllValues().getFirst().stream()
      .map(NewTopic::getName)
      .toList();
  }

  private Future<Void> createKafkaTopicsAsync(KafkaAdminClient client) {
    try (var mocked = mockStatic(KafkaAdminClient.class)) {
      mocked.when(() -> KafkaAdminClient.create(eq(vertx), anyMap())).thenReturn(client);

      return new KafkaAdminClientService(vertx)
        .createKafkaTopics(InventoryKafkaTopic.values(), STUB_TENANT);
    }
  }
}
