package org.folio.it.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.HttpStatus.SC_CREATED;
import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;
import static org.folio.support.ResourcePaths.HOLDINGS;
import static org.folio.support.ResourcePaths.ITEMS;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.UUID;
import org.folio.it.HoldingsStorageFixtures;
import org.folio.it.LocationStorageFixtures;
import org.folio.support.builders.HoldingRequestBuilder;
import org.folio.support.builders.ItemRequestBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InstanceStorageSearchIT extends InstanceStorageTestBase {

  private static final String STAFF_SUPPRESS = "staffSuppress";

  @Test
  @DisplayName("should search by classification number without an array modifier")
  void shouldSearchByClassificationNumber_withoutArrayModifier() {
    var classificationTypeId = UUID.randomUUID().toString();
    createInstance(smallAngryPlanet(UUID.randomUUID()).put("classifications", new JsonArray().add(
      new JsonObject().put("classificationTypeId", classificationTypeId).put("classificationNumber", "K1 .M385"))));
    createInstance(nod(UUID.randomUUID()).put("classifications", new JsonArray().add(
      new JsonObject().put("classificationTypeId", classificationTypeId).put("classificationNumber", "KB1 .A437"))));

    var allInstances = searchForInstances("classifications =\"K1 .M385\"");

    assertThat(allInstances.getInteger(TOTAL_RECORDS_KEY)).isEqualTo(1);
    assertThat(allInstances.getJsonArray(INSTANCES_KEY).getJsonObject(0).getString("title"))
      .isEqualTo("Long Way to a Small Angry Planet");
  }

  @Test
  @DisplayName("should search using the metadata date-updated index")
  void shouldSearchUsingMetadataDateUpdatedIndex() throws InterruptedException {
    createInstance(smallAngryPlanet(UUID.randomUUID()));
    Thread.sleep(2000); // ensure a different "updatedDate" than the first instance
    var second = createInstance(nod(UUID.randomUUID()));

    var metadata = second.getJsonObject(METADATA_KEY);
    var query = "metadata.updatedDate>=" + metadata.getString("updatedDate");

    var responseBody = searchForInstances(query);
    var allInstances = responseBody.getJsonArray(INSTANCES_KEY);

    assertThat(allInstances).hasSize(1);
    assertThat(allInstances.getJsonObject(0).getString("title")).isEqualTo(second.getString("title"));
  }

  @Test
  @DisplayName("should return an instance when filtering by tags")
  void shouldReturnInstance_whenFilteringByTags() {
    var tagValue = "important";
    var instanceWithTag = smallAngryPlanet(UUID.randomUUID())
      .put("tags", new JsonObject().put("tagList", new JsonArray().add(tagValue)));
    createInstance(instanceWithTag);
    createInstance(nod(UUID.randomUUID()));

    var responseBody = searchForInstances("tags.tagList=" + tagValue);
    var instances = responseBody.getJsonArray(INSTANCES_KEY);

    assertThat(instances).hasSize(1);
    assertThat(responseBody.getInteger(TOTAL_RECORDS_KEY)).isEqualTo(1);
    var tagList = instances.getJsonObject(0).getJsonObject("tags").getJsonArray("tagList");
    assertThat(tagList).contains(tagValue);
  }

  @Test
  @DisplayName("should search by discovery-suppress property")
  void shouldSearchByDiscoverySuppressProperty() {
    var suppressed = createInstance(smallAngryPlanet(UUID.randomUUID()).put(DISCOVERY_SUPPRESS, true));
    var notSuppressed = createInstance(smallAngryPlanet(UUID.randomUUID()));

    var suppressedResults = searchForInstances(DISCOVERY_SUPPRESS + "==true").getJsonArray(INSTANCES_KEY);
    var notSuppressedResults = searchForInstances(DISCOVERY_SUPPRESS + "==false").getJsonArray(INSTANCES_KEY);

    assertThat(suppressedResults).hasSize(1);
    assertThat(suppressedResults.getJsonObject(0).getString("id")).isEqualTo(suppressed.getString("id"));
    assertThat(notSuppressedResults).hasSize(1);
    assertThat(notSuppressedResults.getJsonObject(0).getString("id")).isEqualTo(notSuppressed.getString("id"));
  }

  @Test
  @DisplayName("should search by staff-suppress property")
  void shouldSearchByStaffSuppressProperty() {
    var suppressed = createInstance(smallAngryPlanet(UUID.randomUUID()).put(STAFF_SUPPRESS, true));
    var notSuppressed = createInstance(smallAngryPlanet(UUID.randomUUID()).put(STAFF_SUPPRESS, false));
    var notSuppressedDefault = createInstance(smallAngryPlanet(UUID.randomUUID()));

    var suppressedResults = searchForInstances(STAFF_SUPPRESS + "==true").getJsonArray(INSTANCES_KEY);
    var notSuppressedResults = searchForInstances("cql.allRecords=1 not " + STAFF_SUPPRESS + "==true")
      .getJsonArray(INSTANCES_KEY);

    assertThat(suppressedResults).hasSize(1);
    assertThat(suppressedResults.getJsonObject(0).getString("id")).isEqualTo(suppressed.getString("id"));
    assertThat(notSuppressedResults).hasSize(2);
    assertThat(notSuppressedResults.stream().map(o -> ((JsonObject) o).getString("id")).toList())
      .containsExactlyInAnyOrder(notSuppressed.getString("id"), notSuppressedDefault.getString("id"));
  }

  @Test
  @DisplayName("should search for instances by title")
  void shouldSearchForInstances_byTitle() {
    canSort("title=\"Upr*\"", "Uprooted");
  }

  @Test
  @DisplayName("should search for instances by a title word")
  void shouldSearchForInstances_byTitleWord() {
    canSort("title=\"Times\"", "Interesting Times");
  }

  @Test
  @DisplayName("should search for instances by title using the adj relation")
  void shouldSearchForInstances_byTitleAdj() {
    canSort("title adj \"Upro*\"", "Uprooted");
  }

  @Test
  @DisplayName("should search for instances using a query similar to the UI look-ahead search")
  void shouldSearchForInstances_usingUiLookAheadStyleQuery() {
    canSort("title=\"upr*\" or contributors=\"name\": \"upr*\" or identifiers=\"value\": \"upr*\"", "Uprooted");
  }

  @Test
  @DisplayName("should search for instances using the identifiers array modifier on value")
  void shouldSearchForInstances_usingIdentifiersArrayModifierOnValue() {
    canSort("identifiers = /@value 9781447294146", "Uprooted");
  }

  @Test
  @DisplayName("should search for instances using the identifiers array modifier on identifierTypeId and value")
  void shouldSearchForInstances_usingIdentifiersArrayModifierOnTypeAndValue() {
    canSort("identifiers = /@identifierTypeId = " + isbnTypeId + " 9781447294146", "Uprooted");
  }

  @Test
  @DisplayName("should search for instances using the identifiers array modifier on identifierTypeId")
  void shouldSearchForInstances_usingIdentifiersArrayModifierOnType() {
    canSort("identifiers = /@identifierTypeId " + asinTypeId, "Nod");
  }

  @Test
  @DisplayName("should search without being vulnerable to SQL injection via special characters")
  void shouldSearch_withoutSqlInjectionVulnerability() {
    create5instances();
    var specialChars = new String[] {"'", "''", "\\\"", "\\\"\\\"", "(", "((", ")", "))", "{", "{{", "}", "}}"};

    for (var s : specialChars) {
      matchInstanceTitles(searchForInstances("title=\"" + s + "Uprooted\""), "Uprooted");
      matchInstanceTitles(searchForInstances("title==\"" + s + "Uprooted\""));
      matchInstanceTitles(searchForInstances("identifiers=\"" + s + "\""));
      matchInstanceTitles(searchForInstances("identifiers==\"" + s + "\""));
    }
  }

  @Test
  @DisplayName("should search by subjects")
  void shouldSearchBySubjects() {
    createInstanceWithTypeAndSource(instanceWithSubjects("first", "foo", "bar", "baz"));
    createInstanceWithTypeAndSource(instanceWithSubjects("second", "abc def ghi", "uvw xyz"));

    matchInstanceTitles(searchForInstances("subjects=foo"), "first");
    matchInstanceTitles(searchForInstances("subjects=bar"), "first");
    matchInstanceTitles(searchForInstances("subjects=baz"), "first");
    matchInstanceTitles(searchForInstances("subjects=abc"), "second");
    matchInstanceTitles(searchForInstances("subjects=def"), "second");
    matchInstanceTitles(searchForInstances("subjects=ghi"), "second");
    matchInstanceTitles(searchForInstances("subjects=uvw"), "second");
    matchInstanceTitles(searchForInstances("subjects=xyz"), "second");
    matchInstanceTitles(searchForInstances("subjects=\"def ghi\""), "second");
    matchInstanceTitles(searchForInstances("subjects=\"uvw xyz\""), "second");
    matchInstanceTitles(searchForInstances("subjects=\"baz bar\""));
    matchInstanceTitles(searchForInstances("subjects=\"abc xyz\""));
  }

  @Test
  @DisplayName("should search by item barcode")
  void shouldSearchByItemBarcode() {
    var expectedInstanceId = UUID.randomUUID();
    var expectedHoldingId = UUID.randomUUID();
    createInstance(smallAngryPlanet(expectedInstanceId));
    createHoldingsForInstance(expectedHoldingId, expectedInstanceId);
    createItemWithBarcode(expectedHoldingId, "706949453641");

    var otherInstanceId = UUID.randomUUID();
    var otherHoldingId = UUID.randomUUID();
    createInstance(nod(otherInstanceId));
    createHoldingsForInstance(otherHoldingId, otherInstanceId);
    createItemWithBarcode(expectedHoldingId, "766043059304");

    canSort("item.barcode==706949453641", "Long Way to a Small Angry Planet");
  }

  @Test
  @DisplayName("should search by item barcode and holdings permanent location")
  void shouldSearchByItemBarcodeAndPermanentLocation() {
    var smallAngryPlanetInstanceId = UUID.randomUUID();
    var mainLibraryHoldingId = UUID.randomUUID();
    var annexHoldingId = UUID.randomUUID();
    var mainLibraryLocationId = createLocationId();
    var annexLocationId = createLocationId();
    createInstance(smallAngryPlanet(smallAngryPlanetInstanceId));
    createHoldingsWithLocationAndItem(mainLibraryHoldingId, smallAngryPlanetInstanceId, mainLibraryLocationId,
      "706949453641");
    createHoldingsWithLocationAndItem(annexHoldingId, smallAngryPlanetInstanceId, annexLocationId, "70704539201");

    var nodInstanceId = UUID.randomUUID();
    var nodHoldingId = UUID.randomUUID();
    createInstance(nod(nodInstanceId));
    createHoldingsWithLocationAndItem(nodHoldingId, nodInstanceId, mainLibraryLocationId, "766043059304");

    canSort("item.barcode==706949453641 and holdingsRecords.permanentLocationId==" + mainLibraryLocationId,
      "Long Way to a Small Angry Planet");
    canSort("holdingsRecords.permanentLocationId=\"" + mainLibraryLocationId + "\" sortBy title/sort.descending",
      "Nod", "Long Way to a Small Angry Planet");
  }

  @Test
  @DisplayName("should still find instances with no holdings or items when searching by title and barcode")
  void shouldFindInstancesWithNoHoldingsOrItems_whenSearchingByTitleAndBarcode() {
    var smallAngryPlanetInstanceId = UUID.randomUUID();
    var mainLibraryHoldingId = UUID.randomUUID();
    final var nodInstanceId = UUID.randomUUID();
    createInstance(smallAngryPlanet(smallAngryPlanetInstanceId));
    createHoldingsForInstance(mainLibraryHoldingId, smallAngryPlanetInstanceId);
    createItemWithBarcode(mainLibraryHoldingId, "706949453641");
    createInstance(nod(nodInstanceId));

    var responseBody = searchForInstances("item.barcode=706949453641* or title=Nod*");

    assertThat(responseBody.getInteger(TOTAL_RECORDS_KEY)).isEqualTo(2);
    var foundIds = responseBody.getJsonArray(INSTANCES_KEY).stream()
      .map(o -> ((JsonObject) o).getString("id")).toList();
    assertThat(foundIds).containsExactlyInAnyOrder(smallAngryPlanetInstanceId.toString(), nodInstanceId.toString());
  }

  @Test
  @DisplayName("should search for the first ISBN with additional hyphens")
  void shouldSearchForFirstIsbn_withAdditionalHyphens() {
    canSort("isbn = 0-552-16754-1", "Interesting Times");
  }

  @Test
  @DisplayName("should search for the first ISBN with additional hyphens and truncation")
  void shouldSearchForFirstIsbn_withAdditionalHyphensAndTruncation() {
    canSort("isbn = 05-5*", "Interesting Times");
  }

  @Test
  @DisplayName("should search for the second ISBN with missing hyphens")
  void shouldSearchForSecondIsbn_withMissingHyphens() {
    canSort("isbn = 9780552167543", "Interesting Times");
  }

  @Test
  @DisplayName("should search for the second ISBN with missing hyphens and truncation")
  void shouldSearchForSecondIsbn_withMissingHyphensAndTruncation() {
    canSort("isbn = 9780* sortBy title", "Interesting Times", "Temeraire");
  }

  @Test
  @DisplayName("should search for the second ISBN with altered hyphens")
  void shouldSearchForSecondIsbn_withAlteredHyphens() {
    canSort("isbn = 9-7-8-055-2167-543", "Interesting Times");
  }

  @Test
  @DisplayName("should not find an ISBN by its tail string")
  void shouldNotFindIsbn_byTailString() {
    canSort("isbn = 552-16754-3");
  }

  @Test
  @DisplayName("should not find an ISBN by an inner string with truncation")
  void shouldNotFindIsbn_byInnerStringWithTruncation() {
    canSort("isbn = 552*");
  }

  @Test
  @DisplayName("should find the first invalid ISBN")
  void shouldFindFirstInvalidIsbn() {
    canSort("invalidIsbn = 12345", "Interesting Times");
  }

  @Test
  @DisplayName("should not find an ISBN in the invalid-ISBN index")
  void shouldNotFindIsbn_inInvalidIsbnIndex() {
    canSort("invalidIsbn = 0552167541");
  }

  @Test
  @DisplayName("should not find an invalid ISBN in the ISBN index")
  void shouldNotFindInvalidIsbn_inIsbnIndex() {
    canSort("isbn = 12345");
  }

  @Test
  @DisplayName("should sort instances ascending by title")
  void shouldSortInstances_ascendingByTitle() {
    canSort("cql.allRecords=1 sortBy title",
      "Interesting Times", "Long Way to a Small Angry Planet", "Nod", "Temeraire", "Uprooted");
  }

  @Test
  @DisplayName("should sort instances descending by title")
  void shouldSortInstances_descendingByTitle() {
    canSort("cql.allRecords=1 sortBy title/sort.descending",
      "Uprooted", "Temeraire", "Nod", "Long Way to a Small Angry Planet", "Interesting Times");
  }

  @Test
  @DisplayName("should answer cross-table queries joining instances and holdings")
  void shouldAnswerCrossTableQueries_joiningInstancesAndHoldings() {
    final var loc1 = createLocationId();
    final var loc2 = createLocationId();
    var idJ1 = UUID.randomUUID();
    var idJ2 = UUID.randomUUID();
    var idJ3 = UUID.randomUUID();
    createInstance(instanceRequest(idJ1, "TEST1", "Long Way to a Small Angry Planet 1"));
    createInstance(instanceRequest(idJ2, "TEST2", "Long Way to a Small Angry Planet 2"));
    createInstance(instanceRequest(idJ3, "TEST3", "Long Way to a Small Angry Planet 3"));
    createHoldingsRecord(idJ1, loc1);
    createHoldingsRecord(idJ2, loc2);
    createHoldingsRecord(idJ3, loc2);

    assertCrossTableQuery("title=Long Way to a Small Angry Planet* sortby title", 3, "TEST1");
    assertCrossTableQuery("title=cql.allRecords=1 sortBy title", 3, "TEST1");
    assertCrossTableQuery("holdingsRecords.permanentLocationId=" + loc2 + " sortBy title", 2, "TEST2");
    assertCrossTableQuery("title=cql.allRecords=1 and holdingsRecords.permanentLocationId="
                          + loc2 + " sortby title", 2, null);
    assertCrossTableQuery("title=cql.allRecords=1 and holdingsRecords.permanentLocationId=abc* sortby"
                          + " holdingsRecords.permanentLocationId", 0, null);
  }

  private static String createLocationId() {
    return LocationStorageFixtures.createLocation(client);
  }

  private static JsonObject instanceRequest(UUID id, String source, String title) {
    return instanceRequest(id, source, title, new JsonArray().add(identifier(isbnTypeId, "9781473619777")),
      new JsonArray().add(contributor(personalNameTypeId, "Chambers, Becky")), new JsonArray().add(TAG_VALUE));
  }

  private static JsonObject instanceWithSubjects(String title, String... subjectValues) {
    var subjects = new JsonArray();
    for (var value : subjectValues) {
      subjects.add(new JsonObject().put("value", value));
    }
    return new JsonObject().put("title", title).put(SUBJECTS_KEY, subjects);
  }

  private static void createInstanceWithTypeAndSource(JsonObject instance) {
    instance.put("source", "test").put("instanceTypeId", instanceTypeId);
    createInstance(instance);
  }

  private static void createHoldingsForInstance(UUID holdingId, UUID instanceId) {
    var sourceId = HoldingsStorageFixtures.createHoldingsRecordsSource(client);
    var request = new HoldingRequestBuilder()
      .withId(holdingId).withSource(UUID.fromString(sourceId))
      .withPermanentLocation(UUID.fromString(createLocationId())).forInstance(instanceId).create();
    var response = await(doPost(client, HOLDINGS, request));
    assertThat(response.status()).isEqualTo(SC_CREATED);
  }

  private static void createHoldingsWithLocationAndItem(UUID holdingId, UUID instanceId, String locationId,
                                                        String barcode) {
    var sourceId = HoldingsStorageFixtures.createHoldingsRecordsSource(client);
    var request = new HoldingRequestBuilder()
      .withId(holdingId).withSource(UUID.fromString(sourceId))
      .withPermanentLocation(UUID.fromString(locationId)).forInstance(instanceId).create();
    var response = await(doPost(client, HOLDINGS, request));
    assertThat(response.status()).isEqualTo(SC_CREATED);
    createItemWithBarcode(holdingId, barcode);
  }

  private static void createItemWithBarcode(UUID holdingId, String barcode) {
    var materialTypeId = HoldingsStorageFixtures.createMaterialType(client);
    var loanTypeId = HoldingsStorageFixtures.createLoanType(client);
    var request = new ItemRequestBuilder()
      .forHolding(holdingId).withBarcode(barcode)
      .withPermanentLoanType(UUID.fromString(loanTypeId)).withMaterialType(UUID.fromString(materialTypeId)).create();
    var response = await(doPost(client, ITEMS, request));
    assertThat(response.status()).isEqualTo(SC_CREATED);
  }

  private static void createHoldingsRecord(UUID instanceId, String locationId) {
    var sourceId = HoldingsStorageFixtures.createHoldingsRecordsSource(client);
    var holding = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("instanceId", instanceId.toString())
      .put("sourceId", sourceId)
      .put("permanentLocationId", locationId);
    var response = await(doPost(client, HOLDINGS, holding));
    assertThat(response.status()).isEqualTo(SC_CREATED);
  }

  private static void matchInstanceTitles(JsonObject jsonObject, String... expectedTitles) {
    var foundInstances = jsonObject.getJsonArray(INSTANCES_KEY);
    var titles = new String[foundInstances.size()];
    for (int i = 0; i < titles.length; i++) {
      titles[i] = foundInstances.getJsonObject(i).getString("title");
    }
    assertThat(titles).isEqualTo(expectedTitles);
    assertThat(jsonObject.getInteger(TOTAL_RECORDS_KEY)).isEqualTo(expectedTitles.length);
  }

  private static void canSort(String cql, String... expectedTitles) {
    create5instances();
    matchInstanceTitles(searchForInstances(cql), expectedTitles);
  }

  private static void assertCrossTableQuery(String cql, int expectedCount, String expectedFirstSource) {
    var responseBody = searchForInstances(cql);
    assertThat(responseBody.getInteger(TOTAL_RECORDS_KEY)).isEqualTo(expectedCount);
    if (expectedFirstSource != null) {
      assertThat(responseBody.getJsonArray(INSTANCES_KEY).getJsonObject(0).getString("source"))
        .isEqualTo(expectedFirstSource);
    }
  }
}
