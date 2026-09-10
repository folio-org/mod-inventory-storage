package org.folio.rest.impl;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_CREATED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.rest.impl.InstanceStorageFixtures.createInstance;
import static org.folio.rest.impl.InstanceStorageFixtures.createInstanceRelationship;
import static org.folio.rest.impl.InstanceStorageFixtures.createInstanceRelationshipType;
import static org.folio.rest.impl.InstanceStorageFixtures.createInstanceType;

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

    assertThat(createInstanceRelationship(client, instance1Id, instance2Id, relationshipTypeId).status())
      .isEqualTo(SC_CREATED);
    assertThat(createInstanceRelationship(client, instance1Id, instance3Id, relationshipTypeId).status())
      .isEqualTo(SC_CREATED);
  }

  @DisplayName("should return 400 when the referenced instance does not exist")
  @Test
  void shouldReturn400_whenInstanceDoesNotExist() {
    var instance1Id = createInstance(client, "Title One", instanceTypeId);
    var nonExistingInstanceId = UUID.randomUUID().toString();

    assertThat(createInstanceRelationship(client, instance1Id, nonExistingInstanceId, relationshipTypeId).status())
      .isEqualTo(SC_BAD_REQUEST);
  }

  @DisplayName("should return 400 when the relationship type does not exist")
  @Test
  void shouldReturn400_whenRelationshipTypeDoesNotExist() {
    var instance1Id = createInstance(client, "Title One", instanceTypeId);
    var instance2Id = createInstance(client, "Title Two", instanceTypeId);
    var nonExistingRelationshipTypeId = UUID.randomUUID().toString();

    assertThat(createInstanceRelationship(client, instance1Id, instance2Id, nonExistingRelationshipTypeId).status())
      .isEqualTo(SC_BAD_REQUEST);
  }
}
