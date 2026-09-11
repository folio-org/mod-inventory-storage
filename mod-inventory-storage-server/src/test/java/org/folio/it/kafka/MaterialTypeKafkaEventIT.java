package org.folio.it.kafka;

import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;

import io.vertx.core.json.JsonObject;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;
import org.folio.it.BaseIntegrationTest;
import org.folio.support.ResourcePaths;
import org.folio.support.messages.MaterialTypeEventMessageChecks;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MaterialTypeKafkaEventIT extends BaseIntegrationTest {

  private static final String NAME_FIELD = "name";
  private static final String SOURCE_FIELD = "source";
  private static final String ID_FIELD = "id";

  private final MaterialTypeEventMessageChecks eventChecks = eventMessageChecks();

  @Test
  @DisplayName("should publish a Kafka event when a material type is created")
  void shouldPublishKafkaEvent_whenMaterialTypeIsCreated() {
    var createdMaterialType = createMaterialType();

    eventChecks.createdMessagePublished(createdMaterialType);
  }

  @Test
  @DisplayName("should publish a Kafka event when a material type is updated")
  void shouldPublishKafkaEvent_whenMaterialTypeIsUpdated() {
    var createdMaterialType = createMaterialType();
    var materialTypeId = createdMaterialType.getString(ID_FIELD);

    var updateRequestBody = new JsonObject()
      .put(ID_FIELD, materialTypeId)
      .put(NAME_FIELD, "updated-" + UUID.randomUUID())
      .put(SOURCE_FIELD, "local");
    var updateResponse =
      await(doPut(client, ResourcePaths.MATERIAL_TYPES + "/" + materialTypeId, updateRequestBody));
    assertThat(updateResponse.status()).isEqualTo(SC_NO_CONTENT);

    var updatedMaterialType = await(doGet(client, ResourcePaths.MATERIAL_TYPES + "/" + materialTypeId)).jsonBody();
    eventChecks.updatedMessagePublished(createdMaterialType, updatedMaterialType);
  }

  @Test
  @DisplayName("should publish a Kafka event when a material type is deleted")
  void shouldPublishKafkaEvent_whenMaterialTypeIsDeleted() {
    var createdMaterialType = createMaterialType();
    var materialTypeId = createdMaterialType.getString(ID_FIELD);

    var deleteResponse = await(doDelete(client, ResourcePaths.MATERIAL_TYPES + "/" + materialTypeId));
    assertThat(deleteResponse.status()).isEqualTo(SC_NO_CONTENT);

    eventChecks.deletedMessagePublished(createdMaterialType);
  }

  private static JsonObject createMaterialType() {
    var request = new JsonObject()
      .put(NAME_FIELD, "created-" + UUID.randomUUID())
      .put(SOURCE_FIELD, "local");
    var response = await(doPost(client, ResourcePaths.MATERIAL_TYPES, request)).jsonBody();
    assertThat(response).isNotNull();
    return response;
  }

  private static MaterialTypeEventMessageChecks eventMessageChecks() {
    try {
      return new MaterialTypeEventMessageChecks(KAFKA_CONSUMER, new URL(wm.baseUrl()));
    } catch (MalformedURLException e) {
      throw new IllegalStateException(e);
    }
  }
}
