package org.folio.it.api;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;
import static org.folio.it.HoldingsStorageFixtures.createHolding;
import static org.folio.it.HoldingsStorageFixtures.createLoanType;
import static org.folio.it.HoldingsStorageFixtures.createMaterialType;
import static org.folio.it.InstanceStorageFixtures.createInstance;
import static org.folio.it.InstanceStorageFixtures.createInstanceType;
import static org.folio.it.LocationStorageFixtures.createLocation;

import io.vertx.core.json.JsonObject;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.folio.it.BaseIntegrationTest;
import org.folio.rest.jaxrs.model.DereferencedItem;
import org.folio.rest.jaxrs.model.DereferencedItems;
import org.folio.rest.jaxrs.model.Item;
import org.folio.support.ResourcePaths;
import org.folio.support.builders.ItemRequestBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers the {@code /item-storage-dereferenced/items} endpoint, which returns items with their
 * holding/instance/location/material-type/loan-type context flattened into the response instead
 * of left as bare foreign-key ids.
 */
class DereferencedItemStorageIT extends BaseIntegrationTest {

  private static final String INSTANCE_TITLE = "Long Way to a Small Angry Planet";
  private static final String PERMANENT_LOCATION_NAME = "test permanent location";
  private static final String TEMPORARY_LOCATION_NAME = "test temporary location";
  private static final String PERMANENT_LOAN_TYPE_NAME = "test permanent loan type";
  private static final String TEMPORARY_LOAN_TYPE_NAME = "test temporary loan type";
  private static final String MATERIAL_TYPE_NAME = "test material type";
  private static final String SMALL_ANGRY_PLANET_BARCODE = "036000291452";
  private static final String NOD_BARCODE = "565578437802";
  private static final String UPROOTED_BARCODE = "657670342075";

  private static String smallAngryPlanetId;
  private static String uprootedId;

  @BeforeAll
  static void seedReferenceData() {
    // WireMockExtension resets all stubs before every test METHOD (via the shared @BeforeEach),
    // but this @BeforeAll runs before any of that - creating a holding checks consortium
    // membership, so the stub needs to already be registered.
    mockUserTenantsForNonConsortiumMember();

    var instanceTypeId = createInstanceType(client);
    var instanceId = createInstance(client, INSTANCE_TITLE, instanceTypeId);
    var holdingId = createHolding(client, instanceId, createLocation(client));
    // item-level locations, deliberately distinct from the holding's own - these are what
    // DereferencedItem.getPermanentLocation()/getTemporaryLocation() are expected to reflect.
    var permanentLocationId = createLocation(client, PERMANENT_LOCATION_NAME);
    var temporaryLocationId = createLocation(client, TEMPORARY_LOCATION_NAME);
    var materialTypeId = createMaterialType(client, MATERIAL_TYPE_NAME);
    var permanentLoanTypeId = createLoanType(client, PERMANENT_LOAN_TYPE_NAME);
    var temporaryLoanTypeId = createLoanType(client, TEMPORARY_LOAN_TYPE_NAME);

    seedItems(holdingId, materialTypeId, permanentLoanTypeId, permanentLocationId,
      temporaryLoanTypeId, temporaryLocationId);
  }

  @Test
  @DisplayName("should find a record by barcode")
  void shouldFindRecordByBarcode() {
    var items = findByCql("barcode==" + SMALL_ANGRY_PLANET_BARCODE);

    assertThat(items.getTotalRecords()).isEqualTo(1);
    assertSmallAngryPlanet(items.getDereferencedItems().getFirst());

    items = findByCql("barcode==" + UPROOTED_BARCODE);

    assertThat(items.getTotalRecords()).isEqualTo(1);
    assertUprooted(items.getDereferencedItems().getFirst());
  }

  @Test
  @DisplayName("should return all records when no CQL query is given")
  void shouldReturnAllRecords_whenNoCqlQueryGiven() {
    assertThat(getAll().getTotalRecords()).isEqualTo(3);
  }

  @Test
  @DisplayName("should return an empty collection when no items are found")
  void shouldReturnEmptyCollection_whenNoItemsFound() {
    assertThat(findByCql("barcode==647671342075").getTotalRecords()).isEqualTo(0);
  }

  @Test
  @DisplayName("should return 400 when the CQL search is invalid")
  void shouldReturn400_whenCqlSearchIsInvalid() {
    assertThat(attemptFindByCql("barcode&647671342075").status()).isEqualTo(SC_BAD_REQUEST);
  }

  @Test
  @DisplayName("should find a record by id")
  void shouldFindRecordById() {
    assertSmallAngryPlanet(findById(smallAngryPlanetId));
    assertUprooted(findById(uprootedId));
  }

  @Test
  @DisplayName("should return 404 when no item is found for an id")
  void shouldReturn404_whenNoItemFoundForId() {
    assertThat(attemptFindById(UUID.randomUUID().toString()).status()).isEqualTo(SC_NOT_FOUND);
  }

  @Test
  @DisplayName("should return 400 when the id is not a valid UUID")
  void shouldReturn400_whenIdIsNotValidUuid() {
    assertThat(attemptFindById("w325b3dc4").status()).isEqualTo(SC_BAD_REQUEST);
  }

  private static void seedItems(String holdingId, String materialTypeId, String permanentLoanTypeId,
                                String permanentLocationId, String temporaryLoanTypeId, String temporaryLocationId) {
    smallAngryPlanetId = createItemAt(holdingId, SMALL_ANGRY_PLANET_BARCODE, materialTypeId, permanentLoanTypeId,
      permanentLocationId);

    createItemAt(holdingId, NOD_BARCODE, materialTypeId, permanentLoanTypeId, permanentLocationId);

    var uprootedRequest = createItemRequest(holdingId, UPROOTED_BARCODE, materialTypeId, permanentLoanTypeId,
      permanentLocationId)
      .put("temporaryLoanTypeId", temporaryLoanTypeId)
      .put("temporaryLocationId", temporaryLocationId);
    uprootedId = await(doPost(client, ResourcePaths.ITEMS, uprootedRequest)).bodyAsClass(Item.class).getId();
  }

  private static String createItemAt(String holdingId, String barcode, String materialTypeId,
                                     String permanentLoanTypeId, String permanentLocationId) {
    var request = createItemRequest(holdingId, barcode, materialTypeId, permanentLoanTypeId, permanentLocationId);
    return await(doPost(client, ResourcePaths.ITEMS, request)).bodyAsClass(Item.class).getId();
  }

  private static JsonObject createItemRequest(String holdingId, String barcode, String materialTypeId,
                                              String permanentLoanTypeId, String permanentLocationId) {
    return new ItemRequestBuilder()
      .withId(UUID.randomUUID())
      .forHolding(UUID.fromString(holdingId))
      .withBarcode(barcode)
      .withMaterialType(UUID.fromString(materialTypeId))
      .withPermanentLoanType(UUID.fromString(permanentLoanTypeId))
      .create()
      .put("permanentLocationId", permanentLocationId);
  }

  private void assertSmallAngryPlanet(DereferencedItem item) {
    assertThat(item.getBarcode()).isEqualTo(SMALL_ANGRY_PLANET_BARCODE);
    assertThat(item.getId()).isEqualTo(smallAngryPlanetId);
    assertCommonFields(item);
    assertThat(item.getTemporaryLocation()).isNull();
    assertThat(item.getEffectiveLocation().getName()).isEqualTo(item.getPermanentLocation().getName());
    assertThat(item.getTemporaryLoanType()).isNull();
  }

  private void assertUprooted(DereferencedItem item) {
    assertThat(item.getBarcode()).isEqualTo(UPROOTED_BARCODE);
    assertThat(item.getId()).isEqualTo(uprootedId);
    assertCommonFields(item);
    assertThat(item.getTemporaryLocation().getName()).isEqualTo(TEMPORARY_LOCATION_NAME);
    assertThat(item.getEffectiveLocation().getName()).isEqualTo(TEMPORARY_LOCATION_NAME);
    assertThat(item.getTemporaryLoanType().getName()).isEqualTo(TEMPORARY_LOAN_TYPE_NAME);
  }

  private void assertCommonFields(DereferencedItem item) {
    assertThat(item.getInstanceRecord().getTitle()).isEqualTo(INSTANCE_TITLE);
    assertThat(item.getPermanentLoanType().getName()).isEqualTo(PERMANENT_LOAN_TYPE_NAME);
    assertThat(item.getMaterialType().getName()).isEqualTo(MATERIAL_TYPE_NAME);
    assertThat(item.getHoldingsRecord().getInstanceId()).isEqualTo(item.getInstanceRecord().getId());
    assertThat(item.getPermanentLocation().getName()).isEqualTo(PERMANENT_LOCATION_NAME);
  }

  private TestResponse attemptFindByCql(String cqlQuery) {
    return await(doGet(client, ResourcePaths.DEREFERENCED_ITEMS + "?query=" + urlEncode(cqlQuery)));
  }

  private TestResponse attemptFindById(String id) {
    return await(doGet(client, ResourcePaths.DEREFERENCED_ITEMS + "/" + urlEncode(id)));
  }

  private DereferencedItems findByCql(String cqlQuery) {
    var response = attemptFindByCql(cqlQuery);
    assertThat(response.status()).isEqualTo(SC_OK);
    return response.bodyAsClass(DereferencedItems.class);
  }

  private DereferencedItem findById(String id) {
    var response = attemptFindById(id);
    assertThat(response.status()).isEqualTo(SC_OK);
    return response.bodyAsClass(DereferencedItem.class);
  }

  private DereferencedItems getAll() {
    var response = await(doGet(client, ResourcePaths.DEREFERENCED_ITEMS));
    assertThat(response.status()).isEqualTo(SC_OK);
    return response.bodyAsClass(DereferencedItems.class);
  }

  private static String urlEncode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
