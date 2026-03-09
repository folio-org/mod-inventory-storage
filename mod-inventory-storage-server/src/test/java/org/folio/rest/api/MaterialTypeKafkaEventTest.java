package org.folio.rest.api;

import static java.util.UUID.randomUUID;
import static org.folio.utility.ModuleUtility.getClient;

import io.vertx.core.json.JsonObject;
import java.util.UUID;
import junitparams.JUnitParamsRunner;
import lombok.SneakyThrows;
import org.folio.rest.support.http.ResourceClient;
import org.folio.rest.support.messages.MaterialTypeEventMessageChecks;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class MaterialTypeKafkaEventTest extends TestBaseWithInventoryUtil {
  static ResourceClient materialTypesClient = ResourceClient.forMaterialTypes(getClient());

  private final MaterialTypeEventMessageChecks materialTypeEventMessageChecks
    = new MaterialTypeEventMessageChecks(KAFKA_CONSUMER);

  @SneakyThrows
  @Before
  public void beforeEach() {
    removeAllEvents();
  }

  @Test
  public void shouldPublishKafkaEvent_whenMaterialTypeIsCreated() {
    var createdMaterialType = materialTypesClient.create(createMaterialTypeJson()).getJson();

    materialTypeEventMessageChecks.createdMessagePublished(createdMaterialType);
  }

  @Test
  public void shouldPublishKafkaEvent_whenMaterialTypeIsUpdated() {
    var createdMaterialType = materialTypesClient.create(createMaterialTypeJson()).getJson();
    var materialTypeId = createdMaterialType.getString("id");

    var updateRequestBody = createUpdateRequestBody(materialTypeId);
    materialTypesClient.attemptToReplace(materialTypeId, updateRequestBody);

    var updatedMaterialType = materialTypesClient.getByIdIfPresent(materialTypeId).getJson();
    materialTypeEventMessageChecks.updatedMessagePublished(createdMaterialType, updatedMaterialType);
  }

  @Test
  public void shouldPublishKafkaEvent_whenMaterialTypeIsDeleted() {
    var createdMaterialType = materialTypesClient.create(createMaterialTypeJson()).getJson();
    var materialTypeId = createdMaterialType.getString("id");

    materialTypesClient.attemptToDelete(UUID.fromString(materialTypeId));

    materialTypeEventMessageChecks.deletedMessagePublished(createdMaterialType);
  }

  private JsonObject createMaterialTypeJson() {
    JsonObject boundWithPart = new JsonObject();
    boundWithPart.put("name", "created-" + randomUUID());
    boundWithPart.put("source", "local");
    return boundWithPart;
  }

  private JsonObject createUpdateRequestBody(String id) {
    JsonObject boundWithPart = new JsonObject();
    boundWithPart.put("id", id);
    boundWithPart.put("name", "updated-" + randomUUID());
    boundWithPart.put("source", "local");
    return boundWithPart;
  }
}
