package org.folio.it;

import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;

import io.vertx.core.http.HttpClient;
import java.util.UUID;
import org.folio.rest.jaxrs.model.CallNumberType;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.HoldingsRecordsSource;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.LoanType;
import org.folio.rest.jaxrs.model.MaterialType;
import org.folio.support.ResourcePaths;
import org.folio.support.builders.HoldingRequestBuilder;
import org.folio.support.builders.ItemRequestBuilder;

/**
 * Creates holdings-storage and item-storage records (plus their reference data) for
 * {@code *IT} tests, rather than relying on shared class-level state (see docs/testing.md).
 * Each call creates a fresh record with a random id.
 */
public final class HoldingsStorageFixtures {

  private HoldingsStorageFixtures() {
  }

  public static String createMaterialType(HttpClient client) {
    var id = UUID.randomUUID().toString();
    return createMaterialType(client, "test material type " + id);
  }

  /**
   * Seeds a material type with an explicit name, for tests that assert on the name itself
   * (e.g. distinguishing "book" from "journal" in an aggregation response) rather than just
   * needing a valid id.
   */
  public static String createMaterialType(HttpClient client, String name) {
    var id = UUID.randomUUID().toString();
    var materialType = new MaterialType().withId(id).withName(name);

    await(BaseIntegrationTest.doPost(
      client, ResourcePaths.MATERIAL_TYPES, BaseIntegrationTest.pojo2JsonObject(materialType)));

    return id;
  }

  public static String createLoanType(HttpClient client) {
    var id = UUID.randomUUID().toString();
    return createLoanType(client, "test loan type " + id);
  }

  /**
   * Seeds a loan type with an explicit name, for tests that assert on the name itself rather
   * than just needing a valid id.
   */
  public static String createLoanType(HttpClient client, String name) {
    var id = UUID.randomUUID().toString();
    var loanType = new LoanType().withId(id).withName(name);

    await(BaseIntegrationTest.doPost(
      client, ResourcePaths.LOAN_TYPES, BaseIntegrationTest.pojo2JsonObject(loanType)));

    return id;
  }

  public static String createHoldingsRecordsSource(HttpClient client) {
    var id = UUID.randomUUID().toString();
    var source = new HoldingsRecordsSource().withId(id).withName("test holdings source " + id);

    await(BaseIntegrationTest.doPost(
      client, ResourcePaths.HOLDINGS_SOURCES, BaseIntegrationTest.pojo2JsonObject(source)));

    return id;
  }

  public static String createHolding(HttpClient client, String instanceId, String permanentLocationId) {
    var request = new HoldingRequestBuilder()
      .forInstance(UUID.fromString(instanceId))
      .withPermanentLocation(UUID.fromString(permanentLocationId))
      .withSource(UUID.fromString(createHoldingsRecordsSource(client)))
      .create();

    var response = await(BaseIntegrationTest.doPost(client, ResourcePaths.HOLDINGS, request));

    return response.bodyAsClass(HoldingsRecord.class).getId();
  }

  /**
   * Creates a holding from a fully assembled builder, for callers that need fields the
   * simpler {@link #createHolding(HttpClient, String, String)} overload doesn't expose
   * (e.g. a temporary location or electronic access entries).
   */
  public static String createHolding(HttpClient client, HoldingRequestBuilder builder) {
    var response = await(BaseIntegrationTest.doPost(client, ResourcePaths.HOLDINGS, builder.create()));

    return response.bodyAsClass(HoldingsRecord.class).getId();
  }

  /**
   * Seeds a {@code call-number-types} row with an explicit id, for tests that need one of the
   * fixed reference-data ids production code keys shelf-key logic off (see
   * {@code CallNumberUtils.LC_CN_TYPE_ID}), rather than a fresh random one.
   */
  public static String createCallNumberType(HttpClient client, String id, String name) {
    var callNumberType = new CallNumberType().withId(id).withName(name).withSource("folio");

    await(BaseIntegrationTest.doPost(
      client, ResourcePaths.CALL_NUMBER_TYPES, BaseIntegrationTest.pojo2JsonObject(callNumberType)));

    return id;
  }

  public static String createItem(HttpClient client, String holdingId,
                                  String materialTypeId, String permanentLoanTypeId) {
    var request = new ItemRequestBuilder()
      .forHolding(UUID.fromString(holdingId))
      .withMaterialType(UUID.fromString(materialTypeId))
      .withPermanentLoanType(UUID.fromString(permanentLoanTypeId))
      .create();

    var response = await(BaseIntegrationTest.doPost(client, ResourcePaths.ITEMS, request));

    return response.bodyAsClass(Item.class).getId();
  }

  /**
   * Creates an item from a fully assembled builder, for callers that need fields the simpler
   * {@link #createItem(HttpClient, String, String, String)} overload doesn't expose (e.g. a
   * barcode or a temporary location).
   */
  public static String createItem(HttpClient client, ItemRequestBuilder builder) {
    var response = await(BaseIntegrationTest.doPost(client, ResourcePaths.ITEMS, builder.create()));

    return response.bodyAsClass(Item.class).getId();
  }
}
