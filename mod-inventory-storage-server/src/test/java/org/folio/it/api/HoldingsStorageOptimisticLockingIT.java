package org.folio.it.api;

import static org.apache.http.HttpStatus.SC_CONFLICT;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.it.HoldingsStorageFixtures.createHoldingsRecordsSource;

import java.util.Map;
import org.folio.rest.tools.utils.OptimisticLockingUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HoldingsStorageOptimisticLockingIT extends HoldingsStorageTestBase {

  @Test
  @DisplayName("should enforce optimistic locking on holding version")
  void shouldEnforceOptimisticLocking_onHoldingVersion() {
    var holding = createHolding(holdingRequest(createInstanceRecord()));
    holding.put(PERMANENT_LOCATION_ID_KEY, annexLibraryLocationId);
    // updating with current _version 1 succeeds and increments _version to 2
    assertThat(updateHolding(holding).status()).isEqualTo(SC_NO_CONTENT);
    holding.put(PERMANENT_LOCATION_ID_KEY, mainLibraryLocationId);
    // updating with outdated _version 1 fails, current _version is 2
    assertThat(updateHolding(holding).status()).isEqualTo(SC_CONFLICT);
    // updating with _version -1 should fail, single holding PUT never allows suppressing optimistic locking
    holding.put("_version", -1);
    assertThat(updateHolding(holding).status()).isEqualTo(SC_CONFLICT);
    // this allow should not apply to single holding PUT, only to batch unsafe
    OptimisticLockingUtil.configureAllowSuppressOptimisticLocking(
      Map.of(OptimisticLockingUtil.DB_ALLOW_SUPPRESS_OPTIMISTIC_LOCKING, "9999-12-31T23:59:59Z"));
    assertThat(updateHolding(holding).status()).isEqualTo(SC_CONFLICT);
  }

  @Test
  @DisplayName("should not update a holding when there are no changes and optimize-updates is enabled")
  void shouldNotUpdateHolding_whenNoChangesAndOptimizeUpdatesEnabled() {
    assertThat(updateOptimizeUpdatesSetting(true).status()).isEqualTo(SC_NO_CONTENT);
    var holding = createHolding(holdingRequest(createInstanceRecord()));

    assertThat(updateHolding(holding).status()).isEqualTo(SC_NO_CONTENT);

    var updated = getHoldingById(holding.getString("id")).jsonBody();
    assertThat(updated.getString("_version")).isEqualTo("1");
  }

  @Test
  @DisplayName("should update a holding when there are no changes and optimize-updates is disabled")
  void shouldUpdateHolding_whenNoChangesAndOptimizeUpdatesDisabled() {
    assertThat(updateOptimizeUpdatesSetting(false).status()).isEqualTo(SC_NO_CONTENT);
    var holding = createHolding(holdingRequest(createInstanceRecord()));

    assertThat(updateHolding(holding).status()).isEqualTo(SC_NO_CONTENT);

    var updated = getHoldingById(holding.getString("id")).jsonBody();
    assertThat(updated.getString("_version")).isEqualTo("2");
  }

  @Test
  @DisplayName("should update a holding when a new source id is provided")
  void shouldUpdateHolding_whenSourceIdProvided() {
    var instanceId = createInstanceRecord();
    var holding = createHolding(holdingRequest(instanceId).withCallNumber("testCallNumber"));
    var newSourceId = createHoldingsRecordsSource(client);

    holding.put("sourceId", newSourceId).put("callNumber", "updatedTestCallNumber");
    assertThat(updateHolding(holding).status()).isEqualTo(SC_NO_CONTENT);

    var updated = getHoldingById(holding.getString("id")).jsonBody();
    assertThat(updated.getString("callNumber")).isEqualTo("updatedTestCallNumber");
  }
}
