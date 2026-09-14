package org.folio.it.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.HttpStatus.SC_CONFLICT;
import static org.folio.HttpStatus.SC_NO_CONTENT;
import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;
import static org.folio.support.ResourcePaths.INSTANCES;

import io.vertx.core.json.JsonObject;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InstanceStorageOptimisticLockingIT extends InstanceStorageTestBase {

  private static final String OPTIMIZE_UPDATES_SETTING_KEY = "inventory.optimize-updates.enabled";

  @Test
  @DisplayName("should enforce optimistic locking on instance version")
  void shouldEnforceOptimisticLocking_onInstanceVersion() {
    var id = UUID.randomUUID();
    createInstance(nod(id));
    var instance = getById(id).jsonBody();
    instance.put("title", "foo");
    // updating with current _version 1 succeeds and increments _version to 2
    assertThat(update(instance).status()).isEqualTo(SC_NO_CONTENT);
    instance.put("title", "bar");
    // updating with outdated _version 1 fails, current _version is 2
    assertThat(update(instance).status()).isEqualTo(SC_CONFLICT);
    // updating with _version -1 should fail, single instance PUT never allows suppressing optimistic locking
    instance.put("_version", -1);
    assertThat(update(instance).status()).isEqualTo(SC_CONFLICT);
  }

  @Test
  @DisplayName("should not update an instance when there are no changes and optimize-updates is enabled")
  void shouldNotUpdateInstance_whenNoChangesAndOptimizeUpdatesEnabled() {
    assertThat(updateOptimizeUpdatesSetting(true).status()).isEqualTo(SC_NO_CONTENT);
    var id = UUID.randomUUID();
    createInstance(nod(id));
    var instance = getById(id).jsonBody();
    instanceMessageChecks.createdMessagePublished(id.toString());

    assertThat(update(instance).status()).isEqualTo(SC_NO_CONTENT);

    var updatedInstance = getById(id).jsonBody();
    assertThat(updatedInstance.getString("_version")).isEqualTo("1");
    assertThat(KAFKA_CONSUMER.getMessagesForInstance(id.toString())).hasSize(1);
  }

  @Test
  @DisplayName("should update an instance when there are no changes and optimize-updates is disabled")
  void shouldUpdateInstance_whenNoChangesAndOptimizeUpdatesDisabled() {
    assertThat(updateOptimizeUpdatesSetting(false).status()).isEqualTo(SC_NO_CONTENT);
    var id = UUID.randomUUID();
    createInstance(nod(id));
    var instance = getById(id).jsonBody();
    instanceMessageChecks.createdMessagePublished(id.toString());

    assertThat(update(instance).status()).isEqualTo(SC_NO_CONTENT);

    var updatedInstance = getById(id).jsonBody();
    assertThat(updatedInstance.getString("_version")).isEqualTo("2");
    instanceMessageChecks.updatedMessagePublished(instance, updatedInstance);
  }

  @Test
  @DisplayName("should return 409 when patching an instance whose version is stale")
  void shouldReturn409_whenPatchingInstanceWithStaleVersion() {
    var newId = createInstance(smallAngryPlanet(UUID.randomUUID())).getString("id");
    var firstPatch = new JsonObject().put("id", newId).put("_version", 1).put("title", "new title");
    assertThat(await(doPatch(client, INSTANCES + "/" + newId, firstPatch)).status())
      .isEqualTo(SC_NO_CONTENT);

    var stalePatch = new JsonObject().put("id", newId).put("_version", 1).put("title", "new title");
    var response = await(doPatch(client, INSTANCES + "/" + newId, stalePatch));

    assertThat(response.status()).isEqualTo(SC_CONFLICT);
  }

  private static TestResponse updateOptimizeUpdatesSetting(boolean value) {
    return await(doPatch(client, "/inventory-settings/" + OPTIMIZE_UPDATES_SETTING_KEY,
      new JsonObject().put("value", value)));
  }
}
