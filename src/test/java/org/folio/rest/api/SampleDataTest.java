package org.folio.rest.api;

import static java.net.HttpURLConnection.HTTP_OK;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.rest.support.JsonArrayHelper.toList;
import static org.folio.rest.support.ResponseHandler.json;
import static org.folio.rest.support.http.InterfaceUrls.boundWithStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.holdingsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.instanceRelationshipsUrl;
import static org.folio.rest.support.http.InterfaceUrls.instancesStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.itemsStorageUrl;
import static org.folio.utility.ModuleUtility.getClient;
import static org.folio.utility.ModuleUtility.prepareTenant;
import static org.folio.utility.ModuleUtility.removeTenant;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.net.URL;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import org.folio.rest.support.Response;
import org.junit.BeforeClass;
import org.junit.Test;

import io.vertx.core.json.JsonObject;
import lombok.SneakyThrows;

public class SampleDataTest extends TestBase {

  /**
   * Remove tenant WITHOUT sample data,
   * recreate tenant (with reference and) WITH sample data
   * Omit update for now.. It hangs for unknown reasons when using Embedded Postgres MODINVSTOR-369
   */
  @SneakyThrows
  @BeforeClass
  public static void beforeAll() {
    TestBase.beforeAll();

    removeTenant(TENANT_ID);
    prepareTenant(TENANT_ID, null, "mod-inventory-storage-1.0.0", true);
  }

  @Test
  public void instanceCount() {
    assertCount(instancesStorageUrl("?limit=100"), "instances", 36);
  }

  @Test
  public void holdingsCount() {
    assertCount(holdingsStorageUrl("?limit=100"), "holdingsRecords", 20);
  }

  @Test
  public void itemCount() {
    assertCount(itemsStorageUrl("?limit=100"), "items", 25);
  }

  @Test
  public void instanceRelationshipsCount() {
    assertCount(instanceRelationshipsUrl("?limit=100"), "instanceRelationships", 5);
  }

  @Test
  public void boundWithPartsCount() {
    assertCount(boundWithStorageUrl("?limit=100"), "boundWithParts", 10);
  }

  /**
   * Assert that entity has a metadata property where createdDate and updatedDate are not null
   * and createdByUserId and updatedByUserId are null.
   */
  private void assertMetadata(JsonObject entity) {
    JsonObject metadata = entity.getJsonObject("metadata");
    assertThat(metadata, is(notNullValue()));
    assertThat(metadata.getString("createdDate"), is(notNullValue()));
    assertThat(metadata.getString("createdByUserId"), is(nullValue()));
    assertThat(metadata.getString("updatedDate"), is(notNullValue()));
    assertThat(metadata.getString("updatedByUserId"), is(nullValue()));
  }

  @Test
  public void instanceChessPlayer() {
    JsonObject instance = get(instancesStorageUrl("/3c4ae3f3-b460-4a89-a2f9-78ce3145e4fc"));
    assertThat(instance.getString("title"), is("The chess player’s mating guide Computer Datei Robert Ris"));
    assertMetadata(instance);
  }

  @Test
  public void holdingsRecordTransparentWater() {
    JsonObject holdingsRecord = get(holdingsStorageUrl("/e9285a1c-1dfc-4380-868c-e74073003f43"));
    assertThat(holdingsRecord.getString("instanceId"), is("e54b1f4d-7d05-4b1a-9368-3c36b75d8ac6"));
    assertThat(holdingsRecord.getString("callNumber"), is("M1366.S67 T73 2017"));
    assertThat(holdingsRecord.getString("permanentLocationId"), is("fcd64ce1-6995-48f0-840e-89ffa2288371"));
    assertMetadata(holdingsRecord);
  }

  @Test
  public void itemSemanticWebPrimer() {
    JsonObject item = get(itemsStorageUrl("/7212ba6a-8dcf-45a1-be9a-ffaa847c4423"));
    JsonObject status = item.getJsonObject("status");
    assertThat(status.getString("name"), is("Available"));
    assertThat(item.getString("holdingsRecordId"), is("e3ff6133-b9a2-4d4c-a1c9-dc1867d4df19"));
    assertThat(item.getString("barcode"), is("10101"));
    assertMetadata(item);
  }

  @Test
  public void instanceRelationshipGlobalAfrica2() {
    JsonObject ir = getInstanceRelationship("e5cea7b1-3c48-428c-bc5e-2efc9ead1924");

    assertThat("Instance relationship could not be found", ir, notNullValue());

    assertThat(ir.getString("superInstanceId"), is("f7e82a1e-fc06-4b82-bb1d-da326cb378ce"));
    assertThat(ir.getString("subInstanceId"), is("04489a01-f3cd-4f9e-9be4-d9c198703f45"));
    assertThat(ir.getString("instanceRelationshipTypeId"), is("30773a27-b485-4dab-aeb6-b8c04fa3cb17"));
    assertMetadata(ir);
  }

  @SneakyThrows
  private JsonObject get(URL url) {
    final var getCompleted = new CompletableFuture<Response>();

    getClient().get(url, TENANT_ID, json(getCompleted));

    final var response = getCompleted.get(10, SECONDS);

    assertThat(response.getStatusCode(), is(HTTP_OK));

    return response.getJson();
  }

  @SneakyThrows
  private JsonObject getInstanceRelationship(String id) {
    final var getCompleted = new CompletableFuture<Response>();

    getClient().get(instanceRelationshipsUrl("?limit=100"), TENANT_ID, json(getCompleted));

    final var response = getCompleted.get(10, SECONDS);
    final var body = response.getJson();

    final var instanceRelationships = toList(
      body.getJsonArray("instanceRelationships"));

    return instanceRelationships.stream()
      .filter(hasId(id))
      .findFirst()
      .orElse(null);
  }

  private static Predicate<JsonObject> hasId(String id) {
    return (JsonObject instanceRelationship) ->
      Objects.equals(instanceRelationship.getString("id"), id);
  }

  @SneakyThrows
  private void assertCount(URL url, String arrayName, int expectedCount) {
    final var getCompleted = new CompletableFuture<Response>();

    getClient().get(url, TENANT_ID, json(getCompleted));

    final var response = getCompleted.get(10, SECONDS);

    final var body = response.getJson();
    final var array = body.getJsonArray(arrayName);

    assertThat(array.size(), is(expectedCount));
    assertThat(body.getInteger("totalRecords"), is(expectedCount));
  }
}
