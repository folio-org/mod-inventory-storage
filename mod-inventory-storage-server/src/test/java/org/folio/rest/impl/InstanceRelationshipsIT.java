package org.folio.rest.impl;

import static org.folio.HttpStatus.HTTP_BAD_REQUEST;
import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.rest.impl.InstanceStorageFixtures.createInstance;
import static org.folio.rest.impl.InstanceStorageFixtures.createInstanceRelationship;
import static org.folio.rest.impl.InstanceStorageFixtures.createInstanceRelationshipType;
import static org.folio.rest.impl.InstanceStorageFixtures.createInstanceType;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InstanceRelationshipsIT extends BaseIntegrationTest {

  private String instanceTypeId;
  private String relationshipTypeId;

  @BeforeEach
  void createFixtures() {
    instanceTypeId = createInstanceType(client);
    relationshipTypeId = createInstanceRelationshipType(client);
  }

  @DisplayName("should create instance relationships when both instances and the relationship type exist")
  @Test
  void shouldCreateInstanceRelationships_whenInstancesAndTypeExist() {
    var instance1Id = createInstance(client, "Title One", instanceTypeId);
    var instance2Id = createInstance(client, "Title Two", instanceTypeId);
    var instance3Id = createInstance(client, "Title Three", instanceTypeId);

    assertEquals(HTTP_CREATED.toInt(),
      createInstanceRelationship(client, instance1Id, instance2Id, relationshipTypeId).status());
    assertEquals(HTTP_CREATED.toInt(),
      createInstanceRelationship(client, instance1Id, instance3Id, relationshipTypeId).status());
  }

  @DisplayName("should return 400 when the referenced instance does not exist")
  @Test
  void shouldReturn400_whenInstanceDoesNotExist() {
    var instance1Id = createInstance(client, "Title One", instanceTypeId);
    var nonExistingInstanceId = UUID.randomUUID().toString();

    assertEquals(HTTP_BAD_REQUEST.toInt(),
      createInstanceRelationship(client, instance1Id, nonExistingInstanceId, relationshipTypeId).status());
  }

  @DisplayName("should return 400 when the relationship type does not exist")
  @Test
  void shouldReturn400_whenRelationshipTypeDoesNotExist() {
    var instance1Id = createInstance(client, "Title One", instanceTypeId);
    var instance2Id = createInstance(client, "Title Two", instanceTypeId);
    var nonExistingRelationshipTypeId = UUID.randomUUID().toString();

    assertEquals(HTTP_BAD_REQUEST.toInt(),
      createInstanceRelationship(client, instance1Id, instance2Id, nonExistingRelationshipTypeId).status());
  }
}
