package org.folio.rest.impl;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.UUID;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.InstanceRelationship;
import org.folio.rest.jaxrs.model.InstanceRelationshipType;
import org.folio.rest.jaxrs.model.InstanceType;
import org.folio.rest.support.builders.InstanceRequestBuilder;

/**
 * Creates instance-storage records for {@code *IT} tests to use as their own fixtures, rather
 * than relying on shared class-level state (see docs/testing.md). Each call creates a fresh
 * record with a random id, so callers never share rows with each other or across test runs.
 */
final class InstanceStorageFixtures {

  private InstanceStorageFixtures() {
  }

  static String createInstanceType(HttpClient client) {
    return createInstanceType(client, null);
  }

  static String createInstanceType(HttpClient client, String tenantId) {
    return createInstanceType(client, tenantId, UUID.randomUUID().toString());
  }

  /**
   * Seeds an instance type with the given {@code id}, for callers that need the same instance
   * type to exist under the same id across more than one tenant's schema (e.g. a shadow-instance
   * update copies its source instance's {@code instanceTypeId} verbatim onto the target tenant,
   * which only satisfies that foreign key if the same id was seeded there too).
   */
  static String createInstanceType(HttpClient client, String tenantId, String id) {
    var instanceType = new InstanceType()
      .withId(id)
      .withName("test instance type " + id)
      .withCode(id.substring(0, 8))
      .withSource("local");
    var body = BaseIntegrationTest.pojo2JsonObject(instanceType);

    BaseIntegrationTest.get(tenantId == null
      ? BaseIntegrationTest.doPost(client, ResourcePaths.INSTANCE_TYPES, body)
      : BaseIntegrationTest.doPost(client, ResourcePaths.INSTANCE_TYPES, tenantId, body));

    return id;
  }

  static String createInstanceRelationshipType(HttpClient client) {
    var id = UUID.randomUUID().toString();
    var relationshipType = new InstanceRelationshipType()
      .withId(id)
      .withName("test relationship type " + id);

    BaseIntegrationTest.get(BaseIntegrationTest.doPost(
      client, ResourcePaths.INSTANCE_RELATIONSHIP_TYPES, BaseIntegrationTest.pojo2JsonObject(relationshipType)));

    return id;
  }

  static String createInstance(HttpClient client, String title, String instanceTypeId) {
    var request = new InstanceRequestBuilder()
      .withTitle(title)
      .withSource("TEST")
      .withInstanceTypeId(UUID.fromString(instanceTypeId))
      .create();

    var response = BaseIntegrationTest.get(BaseIntegrationTest.doPost(client, ResourcePaths.INSTANCES, request));

    return response.bodyAsClass(Instance.class).getId();
  }

  static String createInstance(HttpClient client, String title, String instanceTypeId, String tenantId) {
    var request = new InstanceRequestBuilder()
      .withTitle(title)
      .withSource("TEST")
      .withInstanceTypeId(UUID.fromString(instanceTypeId))
      .create();

    var response = BaseIntegrationTest.get(
      BaseIntegrationTest.doPost(client, ResourcePaths.INSTANCES, tenantId, request));

    return response.bodyAsClass(Instance.class).getId();
  }

  static BaseIntegrationTest.TestResponse createInstanceRelationship(
    HttpClient client, String superInstanceId, String subInstanceId, String relationshipTypeId) {

    var relationship = new InstanceRelationship()
      .withSuperInstanceId(superInstanceId)
      .withSubInstanceId(subInstanceId)
      .withInstanceRelationshipTypeId(relationshipTypeId);

    return BaseIntegrationTest.get(BaseIntegrationTest.doPost(
      client, ResourcePaths.INSTANCE_RELATIONSHIPS, BaseIntegrationTest.pojo2JsonObject(relationship)));
  }

  /**
   * Builds an instance request body without posting it, for callers that need to submit it via
   * something other than a single {@code POST} (e.g. a batch or bulk endpoint).
   */
  static JsonObject createInstanceRequest(UUID id, String source, String title, JsonArray identifiers,
                                          JsonArray contributors, UUID instanceTypeId, JsonArray tags) {
    var instanceToCreate = new JsonObject();

    if (id != null) {
      instanceToCreate.put("id", id.toString());
    }

    instanceToCreate.put("title", title);
    instanceToCreate.put("source", source);
    instanceToCreate.put("identifiers", identifiers);
    instanceToCreate.put("contributors", contributors);
    if (instanceTypeId != null) {
      instanceToCreate.put("instanceTypeId", instanceTypeId.toString());
    }
    instanceToCreate.put("tags", new JsonObject().put("tagList", tags));
    instanceToCreate.put("_version", 1);
    return instanceToCreate;
  }
}
