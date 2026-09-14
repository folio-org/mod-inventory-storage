package org.folio.it.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.HttpStatus.SC_CONFLICT;
import static org.folio.HttpStatus.SC_NO_CONTENT;
import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;

import io.vertx.core.json.JsonObject;
import java.util.Map;
import java.util.UUID;
import org.folio.rest.tools.utils.OptimisticLockingUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ItemStorageOptimisticLockingIT extends ItemStorageTestBase {

  @Test
  @DisplayName("should enforce optimistic locking on item version")
  void shouldEnforceOptimisticLocking_onItemVersion() {
    var holdingId = createHoldingRecord();
    var item = createItem(smallAngryPlanet(UUID.randomUUID(), holdingId));
    item.put("permanentLocationId", annexLibraryLocationId);
    // updating with current _version 1 succeeds and increments _version to 2
    assertThat(updateItem(item).status()).isEqualTo(SC_NO_CONTENT);
    item.put("permanentLocationId", mainLibraryLocationId);
    // updating with outdated _version 1 fails, current _version is 2
    assertThat(updateItem(item).status()).isEqualTo(SC_CONFLICT);
    // updating with _version -1 should fail, single item PUT never allows suppressing optimistic locking
    item.put("_version", -1);
    assertThat(updateItem(item).status()).isEqualTo(SC_CONFLICT);
    // this allow should not apply to single item PUT, only to batch unsafe
    OptimisticLockingUtil.configureAllowSuppressOptimisticLocking(
      Map.of(OptimisticLockingUtil.DB_ALLOW_SUPPRESS_OPTIMISTIC_LOCKING, "9999-12-31T23:59:59Z"));
    assertThat(updateItem(item).status()).isEqualTo(SC_CONFLICT);
  }

  @Test
  @DisplayName("should not update an item when there are no changes and optimize-updates is enabled")
  void shouldNotUpdateItem_whenNoChangesAndOptimizeUpdatesEnabled() {
    assertThat(updateOptimizeUpdatesSetting(true).status()).isEqualTo(SC_NO_CONTENT);
    var holdingId = createHoldingRecord();
    var item = createItem(smallAngryPlanet(UUID.randomUUID(), holdingId));

    assertThat(updateItem(item).status()).isEqualTo(SC_NO_CONTENT);

    assertThat(getItemJsonById(item.getString("id")).getString("_version")).isEqualTo("1");
  }

  @Test
  @DisplayName("should update an item when there are no changes and optimize-updates is disabled")
  void shouldUpdateItem_whenNoChangesAndOptimizeUpdatesDisabled() {
    assertThat(updateOptimizeUpdatesSetting(false).status()).isEqualTo(SC_NO_CONTENT);
    var holdingId = createHoldingRecord();
    var item = createItem(smallAngryPlanet(UUID.randomUUID(), holdingId));

    assertThat(updateItem(item).status()).isEqualTo(SC_NO_CONTENT);

    assertThat(getItemJsonById(item.getString("id")).getString("_version")).isEqualTo("2");
  }

  private static TestResponse updateOptimizeUpdatesSetting(boolean value) {
    return await(doPatch(client, "/inventory-settings/inventory.optimize-updates.enabled",
      new JsonObject().put("value", value)));
  }
}
