package org.folio.it.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.HttpStatus.SC_BAD_REQUEST;
import static org.folio.HttpStatus.SC_CREATED;
import static org.folio.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.folio.HttpStatus.SC_NO_CONTENT;
import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;

import io.vertx.core.json.JsonObject;
import java.util.UUID;
import org.folio.support.ResourcePaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HoldingsStorageHridIT extends HoldingsStorageTestBase {

  @Test
  @DisplayName("should create a holding when a hrid is supplied")
  void shouldCreateHolding_whenHridIsSupplied() {
    var holding = createHolding(withHrid(holdingRequest(createInstanceRecord()), "TEST1001"));

    assertThat(holding.getString("hrid")).isEqualTo("TEST1001");
    assertThat(getHoldingById(holding.getString("id")).jsonBody().getString("hrid")).isEqualTo("TEST1001");
  }

  @Test
  @DisplayName("should return 422 when creating a holding with a duplicate hrid")
  void shouldReturn422_whenCreatingHoldingWithDuplicateHrid() {
    var instanceId = createInstanceRecord();
    var holding = createHolding(withHrid(holdingRequest(instanceId), "ho00000000001"));
    assertThat(holding.getString("hrid")).isEqualTo("ho00000000001");

    var duplicate = withHrid(holdingRequest(instanceId), "ho00000000001").create();
    var response = await(doPost(client, ResourcePaths.HOLDINGS, duplicate));

    assertHridError(response, "ho00000000001");
  }

  @Test
  @DisplayName("should return 500 when hrid generation fails because the sequence is exhausted")
  void shouldReturn500_whenHridGenerationFails() {
    var instanceId = createInstanceRecord();
    setHoldingsSequence(99_999_999_999L);
    var holding = createHolding(holdingRequest(instanceId));
    assertThat(holding.getString("hrid")).isEqualTo("ho99999999999");

    var response = await(doPost(client, ResourcePaths.HOLDINGS, holdingRequest(instanceId).create()));

    assertThat(response.status()).isEqualTo(SC_INTERNAL_SERVER_ERROR);
    assertThat(response.body().toString()).contains("hrid_holdings_seq");
  }

  @Test
  @DisplayName("should return 422 when the hrid is already allocated")
  void shouldReturn422_whenHridAlreadyAllocated() {
    var instanceId = createInstanceRecord();
    setHoldingsSequence(1000L);
    var request = holdingRequest(instanceId).create();
    var first = createHolding(request);
    assertThat(first.getString("hrid")).isEqualTo("ho00000001000");

    setHoldingsSequence(1000L);
    var response = await(doPost(client, ResourcePaths.HOLDINGS, holdingRequest(instanceId).create()));

    assertHridError(response, "ho00000001000");
  }

  @Test
  @DisplayName("should return 400 when changing the hrid after creation")
  void shouldReturn400_whenChangingHridAfterCreation() {
    var holding = createHolding(holdingRequest(createInstanceRecord()));
    assertThat(holding.getString("hrid")).isEqualTo("ho00000000001");
    holding.put("hrid", "ABC123");

    var response = updateHolding(holding);

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body()).hasToString("The hrid field cannot be changed: new=ABC123, old=ho00000000001");
  }

  @Test
  @DisplayName("should return 400 when removing the hrid after creation")
  void shouldReturn400_whenRemovingHridAfterCreation() {
    var holding = createHolding(holdingRequest(createInstanceRecord()));
    assertThat(holding.getString("hrid")).isEqualTo("ho00000000001");
    holding.remove("hrid");

    var response = updateHolding(holding);

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body()).hasToString("The hrid field cannot be changed: new=null, old=ho00000000001");
  }

  @Test
  @DisplayName("should create a holding via PUT when a hrid is supplied")
  void shouldCreateHolding_viaPutWhenHridSupplied() {
    var holdingId = UUID.randomUUID();
    var request = withHrid(holdingRequest(createInstanceRecord()), "TEST1001").withId(holdingId).create();

    assertThat(updateHolding(request).status()).isEqualTo(SC_NO_CONTENT);

    var holding = getHoldingById(holdingId).jsonBody();
    assertThat(holding.getString("hrid")).isEqualTo("TEST1001");
  }

  @Test
  @DisplayName("should generate hrids for a synchronous batch")
  void shouldGenerateHrids_forSynchronousBatch() {
    setHoldingsSequence(1);
    var holdingsArray = threeHoldingsRequest();

    assertThat(syncBatch(holdingsArray).status()).isEqualTo(SC_CREATED);

    holdingsArray.stream().map(JsonObject.class::cast).forEach(request -> {
      var holding = getHoldingById(request.getString("id")).jsonBody();
      assertThat(holding.getString("hrid")).isBetween("ho00000000001", "ho00000000003");
      assertThat(holding.getString(EFFECTIVE_LOCATION_ID_KEY)).isEqualTo(holding.getString(PERMANENT_LOCATION_ID_KEY));
    });
  }

  @Test
  @DisplayName("should generate hrids for the holdings missing one in a synchronous batch")
  void shouldGenerateHrids_forHoldingsMissingOneInBatch() {
    setHoldingsSequence(1);
    var hrid = "ABC123";
    var holdingsArray = threeHoldingsRequest();
    holdingsArray.getJsonObject(1).put("hrid", hrid);

    assertThat(syncBatch(holdingsArray).status()).isEqualTo(SC_CREATED);

    var first = getHoldingById(holdingsArray.getJsonObject(0).getString("id")).jsonBody();
    assertThat(first.getString("hrid")).isBetween("ho00000000001", "ho00000000002");
    var second = getHoldingById(holdingsArray.getJsonObject(1).getString("id")).jsonBody();
    assertThat(second.getString("hrid")).isEqualTo(hrid);
    var third = getHoldingById(holdingsArray.getJsonObject(2).getString("id")).jsonBody();
    assertThat(third.getString("hrid")).isBetween("ho00000000001", "ho00000000002");
  }

  @Test
  @DisplayName("should return 422 when a synchronous batch has duplicate hrids")
  void shouldReturn422_whenBatchHasDuplicateHrids() {
    setHoldingsSequence(1);
    var duplicateHrid = "ho00000000001";
    var holdingsArray = threeHoldingsRequest();
    holdingsArray.getJsonObject(1).put("hrid", duplicateHrid);

    var response = syncBatch(holdingsArray);

    assertHridError(response, duplicateHrid);
    holdingsArray.forEach(holding ->
      assertGetNotFound(ResourcePaths.HOLDINGS + "/" + ((JsonObject) holding).getString("id")));
  }

  @Test
  @DisplayName("should return 500 when hrid generation fails for a synchronous batch")
  void shouldReturn500_whenBatchHridGenerationFails() {
    setHoldingsSequence(99_999_999_999L);
    var holdingsArray = threeHoldingsRequest();

    var response = syncBatch(holdingsArray);

    assertThat(response.status()).isEqualTo(SC_INTERNAL_SERVER_ERROR);
    assertThat(response.body().toString()).contains("hrid_holdings_seq");
    holdingsArray.forEach(holding ->
      assertGetNotFound(ResourcePaths.HOLDINGS + "/" + ((JsonObject) holding).getString("id")));
  }
}
