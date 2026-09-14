package org.folio.it.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.HttpStatus.SC_BAD_REQUEST;
import static org.folio.HttpStatus.SC_CREATED;
import static org.folio.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.folio.HttpStatus.SC_NO_CONTENT;
import static org.folio.HttpStatus.SC_UNPROCESSABLE_CONTENT;
import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;
import static org.folio.support.ResourcePaths.INSTANCES;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.UUID;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Instance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InstanceStorageHridIT extends InstanceStorageTestBase {

  @Test
  @DisplayName("should generate an instance hrid when none is supplied")
  void shouldGenerateInstanceHrid_whenNoneSupplied() {
    var id = UUID.randomUUID();
    var instanceToCreate = smallAngryPlanet(id);
    instanceToCreate.remove("hrid");
    setInstanceSequence(1);

    createInstance(instanceToCreate);

    assertThat(getById(id).jsonBody().getString("hrid")).isEqualTo("in00000000001");
  }

  @Test
  @DisplayName("should create an instance when a hrid is supplied")
  void shouldCreateInstance_whenHridSupplied() {
    var id = UUID.randomUUID();
    var instanceToCreate = smallAngryPlanet(id).put("hrid", "testHRID");

    createInstance(instanceToCreate);

    assertThat(getById(id).jsonBody().getString("hrid")).isEqualTo("testHRID");
  }

  @Test
  @DisplayName("should return 400 when creating an instance with a duplicate hrid")
  void shouldReturn400_whenCreatingInstanceWithDuplicateHrid() {
    var id = UUID.randomUUID();
    var instanceToCreate = smallAngryPlanet(id);
    instanceToCreate.remove("hrid");
    setInstanceSequence(1);
    createInstance(instanceToCreate);
    assertThat(getById(id).jsonBody().getString("hrid")).isEqualTo("in00000000001");

    var duplicate = nod(UUID.randomUUID()).put("hrid", "in00000000001");
    var response = await(doPost(client, INSTANCES, duplicate));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body()).hasToString("HRID value already exists in table instance: in00000000001");
  }

  @Test
  @DisplayName("should return 500 when hrid generation fails because the sequence is exhausted")
  void shouldReturn500_whenHridGenerationFails() {
    var id = UUID.randomUUID();
    var instanceToCreate = smallAngryPlanet(id);
    instanceToCreate.remove("hrid");
    setInstanceSequence(99_999_999_999L);
    createInstance(instanceToCreate);
    assertThat(getById(id).jsonBody().getString("hrid")).isEqualTo("in99999999999");

    var instanceToFail = nod(UUID.randomUUID());
    instanceToFail.remove("hrid");
    var response = await(doPost(client, INSTANCES, instanceToFail));

    assertThat(response.status()).isEqualTo(SC_INTERNAL_SERVER_ERROR);
    assertThat(response.body().toString()).contains("hrid_instances_seq");
  }

  @Test
  @DisplayName("should return 400 when changing the hrid after creation")
  void shouldReturn400_whenChangingHridAfterCreation() {
    var id = UUID.randomUUID();
    var instanceToCreate = smallAngryPlanet(id);
    instanceToCreate.remove("hrid");
    setInstanceSequence(1);
    createInstance(instanceToCreate);
    var instance = getById(id).jsonBody();
    assertThat(instance.getString("hrid")).isEqualTo("in00000000001");
    instance.put("hrid", "testHRID");

    var response = await(doPut(client, INSTANCES + "/" + id, instance));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body())
      .hasToString("The hrid field cannot be changed: new=testHRID, old=in00000000001");
  }

  @Test
  @DisplayName("should allow changing the hrid when the source is consortia")
  void shouldAllowChangingHrid_whenSourceIsConsortia() {
    var id = UUID.randomUUID();
    createInstance(smallAngryPlanet(id).put("hrid", "oldHRID"));
    var newHrid = "newHRID";
    var instance = getById(id).jsonBody().put("source", "CONSORTIUM-MARC").put("hrid", newHrid);

    var updated = updateInstance(instance);

    assertThat(updated.mapTo(Instance.class).getHrid()).isEqualTo(newHrid);
  }

  @Test
  @DisplayName("should return 400 when creating an instance whose hrid is already allocated")
  void shouldReturn400_whenCreatingInstanceWithAlreadyAllocatedHrid() {
    var instanceRequest = smallAngryPlanet(UUID.randomUUID());
    instanceRequest.remove("id");
    instanceRequest.remove("hrid");
    setInstanceSequence(1000L);

    var firstAllocation = await(doPost(client, INSTANCES, instanceRequest)).jsonBody();
    assertThat(firstAllocation.getString("hrid")).isEqualTo("in00000001000");

    setInstanceSequence(1000L);
    var response = await(doPost(client, INSTANCES, instanceRequest));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body()).hasToString("HRID value already exists in table instance: in00000001000");
  }

  @Test
  @DisplayName("should return 400 when removing the hrid after creation")
  void shouldReturn400_whenRemovingHridAfterCreation() {
    var id = UUID.randomUUID();
    var instanceToCreate = smallAngryPlanet(id);
    instanceToCreate.remove("hrid");
    setInstanceSequence(1);
    createInstance(instanceToCreate);
    var instance = getById(id).jsonBody();
    assertThat(instance.getString("hrid")).isEqualTo("in00000000001");
    instance.remove("hrid");

    var response = await(doPut(client, INSTANCES + "/" + id, instance));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body()).hasToString("The hrid field cannot be changed: new=null, old=in00000000001");
  }

  @Test
  @DisplayName("should generate hrids for a synchronous batch")
  void shouldGenerateHrids_forSynchronousBatch() {
    var instancesArray = new JsonArray()
      .add(uprooted(UUID.randomUUID())).add(smallAngryPlanet(UUID.randomUUID())).add(temeraire(UUID.randomUUID()));
    setInstanceSequence(1);

    var response = syncBatch(instancesArray);
    assertThat(response.status()).isEqualTo(SC_CREATED);

    for (int i = 0; i < 3; i++) {
      var hrid = getById(instancesArray.getJsonObject(i).getString("id")).jsonBody().getString("hrid");
      assertThat(hrid).isBetween("in00000000001", "in00000000003");
    }
  }

  @Test
  @DisplayName("should generate hrids for the instances in a batch missing one, alongside existing hrids")
  void shouldGenerateHrids_forInstancesMissingOneInBatch() {
    var ids = new UUID[5];
    var instancesArray = new JsonArray()
      .add(uprooted(ids[0] = UUID.randomUUID()))
      .add(uprooted(ids[1] = UUID.randomUUID()).put("hrid", "foo"))
      .add(uprooted(ids[2] = UUID.randomUUID()))
      .add(uprooted(ids[3] = UUID.randomUUID()).put("hrid", "bar"))
      .add(uprooted(ids[4] = UUID.randomUUID()));
    setInstanceSequence(1);

    var response = syncBatch(instancesArray);
    assertThat(response.status()).isEqualTo(SC_CREATED);

    assertThat(getById(ids[0]).jsonBody().getString("hrid")).isEqualTo("in00000000001");
    assertThat(getById(ids[1]).jsonBody().getString("hrid")).isEqualTo("foo");
    assertThat(getById(ids[2]).jsonBody().getString("hrid")).isEqualTo("in00000000002");
    assertThat(getById(ids[3]).jsonBody().getString("hrid")).isEqualTo("bar");
    assertThat(getById(ids[4]).jsonBody().getString("hrid")).isEqualTo("in00000000003");

    var nextHrid = await(doPost(client, INSTANCES, uprooted(UUID.randomUUID())))
      .jsonBody().getString("hrid");
    assertThat(nextHrid).isEqualTo("in00000000004");
  }

  @Test
  @DisplayName("should return 422 when a synchronous batch has duplicate hrids")
  void shouldReturn422_whenSynchronousBatchHasDuplicateHrids() {
    var instancesArray = new JsonArray().add(uprooted(UUID.randomUUID()));
    instancesArray.add(temeraire(UUID.randomUUID()).put("hrid", "in00000000001"));
    setInstanceSequence(1);

    var response = syncBatch(instancesArray);

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
    var errors = response.jsonBody().mapTo(Errors.class);
    assertThat(errors.getErrors()).hasSize(1);
    var parameter = errors.getErrors().getFirst().getParameters().getFirst();
    assertThat(parameter.getKey()).contains("'hrid'");
    assertThat(parameter.getValue()).isEqualTo("in00000000001");
  }

  @Test
  @DisplayName("should return 500 when a synchronous batch fails to generate a hrid")
  void shouldReturn500_whenSynchronousBatchHridGenerationFails() {
    var instancesArray = new JsonArray().add(uprooted(UUID.randomUUID()));
    instancesArray.add(temeraire(UUID.randomUUID()).put("hrid", ""));
    setInstanceSequence(99_999_999_999L);

    var response = syncBatch(instancesArray);

    assertThat(response.status()).isEqualTo(SC_INTERNAL_SERVER_ERROR);
    assertThat(response.body().toString()).contains("hrid_instances_seq");
  }

  @Test
  @DisplayName("should return 400 when patching an instance with a changed hrid")
  void shouldReturn400_whenPatchingInstanceWithChangedHrid() {
    var newId = createInstance(smallAngryPlanet(UUID.randomUUID())).getString("id");
    var patchJson = new JsonObject().put("id", newId).put("_version", 1).put("hrid", "12345");

    var response = await(doPatch(client, INSTANCES + "/" + newId, patchJson));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
  }

  @Test
  @DisplayName("should patch an instance when the hrid is unchanged")
  void shouldPatchInstance_whenHridIsUnchanged() {
    var newId = createInstance(smallAngryPlanet(UUID.randomUUID())).getString("id");
    var existingHrid = getById(newId).jsonBody().getString("hrid");
    var patchJson = new JsonObject().put("id", newId).put("_version", 1).put("hrid", existingHrid);

    var response = await(doPatch(client, INSTANCES + "/" + newId, patchJson));

    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
  }
}
