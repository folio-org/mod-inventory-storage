package org.folio.rest.impl;

import io.vertx.core.http.HttpClient;
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
    var id = UUID.randomUUID().toString();
    var instanceType = new InstanceType()
      .withId(id)
      .withName("test instance type " + id)
      .withCode(id.substring(0, 8))
      .withSource("local");

    BaseIntegrationTest.get(BaseIntegrationTest.doPost(
      client, ResourcePaths.INSTANCE_TYPES, BaseIntegrationTest.pojo2JsonObject(instanceType)));

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
}
