package org.folio.rest.api;

import static java.util.UUID.randomUUID;
import static org.folio.utility.ModuleUtility.getClient;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.vertx.core.json.JsonObject;
import java.net.HttpURLConnection;
import java.util.UUID;
import lombok.SneakyThrows;
import org.folio.rest.support.http.ResourceClient;
import org.folio.rest.support.messages.MaterialTypeEventMessageChecks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MaterialTypeKafkaEventTest extends TestBaseWithInventoryUtil {
  static ResourceClient materialTypesClient = ResourceClient.forMaterialTypes(getClient());

  private final MaterialTypeEventMessageChecks materialTypeEventMessageChecks
    = new MaterialTypeEventMessageChecks(KAFKA_CONSUMER);

  @SneakyThrows
  @BeforeEach
  void beforeEach() {
    removeAllEvents();
  }

  @Test
  void shouldPublishKafkaEvent_whenMaterialTypeIsCreated() {
    var createdMaterialType = materialTypesClient.create(getCreateMaterialTypeRequestBody()).getJson();
    assertNotNull(createdMaterialType);

    materialTypeEventMessageChecks.createdMessagePublished(createdMaterialType);
  }

  @Test
  void shouldPublishKafkaEvent_whenMaterialTypeIsUpdated() {
    var createdMaterialType = materialTypesClient.create(getCreateMaterialTypeRequestBody()).getJson();
    assertNotNull(createdMaterialType);
    var materialTypeId = createdMaterialType.getString("id");

    var updateRequestBody = getUpdateMaterialTypeRequestBody(materialTypeId);
    var updateResponse = materialTypesClient.attemptToReplace(materialTypeId, updateRequestBody);
    assertThat(updateResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    var updatedMaterialType = materialTypesClient.getByIdIfPresent(materialTypeId).getJson();
    materialTypeEventMessageChecks.updatedMessagePublished(createdMaterialType, updatedMaterialType);
  }

  @Test
  void shouldPublishKafkaEvent_whenMaterialTypeIsDeleted() {
    var createdMaterialType = materialTypesClient.create(getCreateMaterialTypeRequestBody()).getJson();
    assertNotNull(createdMaterialType);
    var materialTypeId = createdMaterialType.getString("id");

    var deleteResponse = materialTypesClient.attemptToDelete(UUID.fromString(materialTypeId));
    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    materialTypeEventMessageChecks.deletedMessagePublished(createdMaterialType);
  }

  private JsonObject getCreateMaterialTypeRequestBody() {
    JsonObject boundWithPart = new JsonObject();
    boundWithPart.put("name", "created-" + randomUUID());
    boundWithPart.put("source", "local");
    return boundWithPart;
  }

  private JsonObject getUpdateMaterialTypeRequestBody(String id) {
    JsonObject boundWithPart = new JsonObject();
    boundWithPart.put("id", id);
    boundWithPart.put("name", "updated-" + randomUUID());
    boundWithPart.put("source", "local");
    return boundWithPart;
  }
}
