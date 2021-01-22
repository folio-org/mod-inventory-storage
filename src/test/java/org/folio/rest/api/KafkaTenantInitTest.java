package org.folio.rest.api;

import static io.vertx.core.json.JsonObject.mapFrom;
import static org.apache.kafka.clients.admin.AdminClient.create;
import static org.folio.rest.api.StorageTestSuite.removeTenant;
import static org.folio.rest.api.StorageTestSuite.tenantOp;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.common.KafkaFuture;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.junit.Test;

import lombok.SneakyThrows;

public class KafkaTenantInitTest extends TestBase {
  private static final String TENANT_TO_USE = "kafka_test";

  @Test
  public void shouldNotCreateKafkaTopicsOnTenantPurge() throws Exception {
    prepareTestTenant();
    final String topicToTest = "inventory.instance";
    final AdminClient kafkaAdminClient = create(getClientConfigs());

    assertThat(get(kafkaAdminClient.listTopics().names()),
      hasItem(topicToTest));

    get(kafkaAdminClient.deleteTopics(Set.of(topicToTest)).all());

    assertThat(get(kafkaAdminClient.listTopics().names()),
      not(hasItem(topicToTest)));

    removeTenant(TENANT_TO_USE);

    assertThat(get(kafkaAdminClient.listTopics().names()),
      not(hasItem(topicToTest)));
  }

  private Map<String, Object> getClientConfigs() {
    return Map.of("bootstrap.servers", "localhost:9092");
  }

  @SneakyThrows
  private <T> T get(KafkaFuture<T> future) {
    return future.get(5, TimeUnit.SECONDS);
  }

  @SneakyThrows
  private void prepareTestTenant() {
    tenantOp(TENANT_TO_USE, mapFrom(new TenantAttributes()
      .withModuleTo("mod-inventory-storage-1.0.0")));
  }
}
