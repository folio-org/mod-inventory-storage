package org.folio.rest.impl;

import io.vertx.core.http.HttpClient;
import java.util.UUID;
import org.folio.rest.jaxrs.model.CallNumberType;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.HoldingsRecordsSource;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.LoanType;
import org.folio.rest.jaxrs.model.MaterialType;
import org.folio.rest.support.builders.HoldingRequestBuilder;
import org.folio.rest.support.builders.ItemRequestBuilder;

/**
 * Creates holdings-storage and item-storage records (plus their reference data) for
 * {@code *IT} tests, rather than relying on shared class-level state (see docs/testing.md).
 * Each call creates a fresh record with a random id.
 */
final class HoldingsStorageFixtures {

  private HoldingsStorageFixtures() {
  }

  static String createMaterialType(HttpClient client) {
    var id = UUID.randomUUID().toString();
    var materialType = new MaterialType().withId(id).withName("test material type " + id);

    BaseIntegrationTest.get(BaseIntegrationTest.doPost(
      client, ResourcePaths.MATERIAL_TYPES, BaseIntegrationTest.pojo2JsonObject(materialType)));

    return id;
  }

  static String createLoanType(HttpClient client) {
    var id = UUID.randomUUID().toString();
    var loanType = new LoanType().withId(id).withName("test loan type " + id);

    BaseIntegrationTest.get(BaseIntegrationTest.doPost(
      client, ResourcePaths.LOAN_TYPES, BaseIntegrationTest.pojo2JsonObject(loanType)));

    return id;
  }

  static String createHoldingsRecordsSource(HttpClient client) {
    var id = UUID.randomUUID().toString();
    var source = new HoldingsRecordsSource().withId(id).withName("test holdings source " + id);

    BaseIntegrationTest.get(BaseIntegrationTest.doPost(
      client, ResourcePaths.HOLDINGS_SOURCES, BaseIntegrationTest.pojo2JsonObject(source)));

    return id;
  }

  static String createHolding(HttpClient client, String instanceId, String permanentLocationId) {
    var request = new HoldingRequestBuilder()
      .forInstance(UUID.fromString(instanceId))
      .withPermanentLocation(UUID.fromString(permanentLocationId))
      .withSource(UUID.fromString(createHoldingsRecordsSource(client)))
      .create();

    var response = BaseIntegrationTest.get(BaseIntegrationTest.doPost(client, ResourcePaths.HOLDINGS, request));

    return response.bodyAsClass(HoldingsRecord.class).getId();
  }

  /**
   * Seeds a {@code call-number-types} row with an explicit id, for tests that need one of the
   * fixed reference-data ids production code keys shelf-key logic off (see
   * {@code CallNumberUtils.LC_CN_TYPE_ID}), rather than a fresh random one.
   */
  static String createCallNumberType(HttpClient client, String id, String name) {
    var callNumberType = new CallNumberType().withId(id).withName(name).withSource("folio");

    BaseIntegrationTest.get(BaseIntegrationTest.doPost(
      client, ResourcePaths.CALL_NUMBER_TYPES, BaseIntegrationTest.pojo2JsonObject(callNumberType)));

    return id;
  }

  static String createItem(HttpClient client, String holdingId, String materialTypeId, String permanentLoanTypeId) {
    var request = new ItemRequestBuilder()
      .forHolding(UUID.fromString(holdingId))
      .withMaterialType(UUID.fromString(materialTypeId))
      .withPermanentLoanType(UUID.fromString(permanentLoanTypeId))
      .create();

    var response = BaseIntegrationTest.get(BaseIntegrationTest.doPost(client, ResourcePaths.ITEMS, request));

    return response.bodyAsClass(Item.class).getId();
  }
}
