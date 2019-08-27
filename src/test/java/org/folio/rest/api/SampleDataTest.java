package org.folio.rest.api;

import static java.net.HttpURLConnection.HTTP_OK;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.rest.api.StorageTestSuite.TENANT_ID;
import static org.folio.rest.support.ResponseHandler.json;
import static org.folio.rest.support.http.InterfaceUrls.holdingsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.instanceRelationshipsUrl;
import static org.folio.rest.support.http.InterfaceUrls.instancesStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.itemsStorageUrl;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.rest.support.Response;
import org.junit.BeforeClass;
import org.junit.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import junit.framework.AssertionFailedError;

public class SampleDataTest extends TestBase {

  /**
   * Remove tenant WITHOUT sample data,
   * recreate tenant (with reference and) WITH sample data.
   */
  @BeforeClass
  public static void beforeAny() throws Exception {
    StorageTestSuite.removeTenant(StorageTestSuite.TENANT_ID);
    StorageTestSuite.prepareTenant(StorageTestSuite.TENANT_ID, /* loadSample= */ true);
  }

  private void assertCount(URL url, String arrayName, int expectedCount) {
    try {
      CompletableFuture<Response> getCompleted = new CompletableFuture<>();
      client.get(url, TENANT_ID, json(getCompleted));
      Response response = getCompleted.get(5, SECONDS);
      JsonObject body = response.getJson();
      JsonArray array = body.getJsonArray(arrayName);
      assertThat(array.size(), is(expectedCount));
      assertThat(body.getInteger("totalRecords"), is(expectedCount));
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void instanceCount() {
    assertCount(instancesStorageUrl("?limit=100"), "instances", 28);
  }

  @Test
  public void holdingsCount() {
    assertCount(holdingsStorageUrl("?limit=100"), "holdingsRecords", 12);
  }

  @Test
  public void itemCount() {
    assertCount(itemsStorageUrl("?limit=100"), "items", 17);
  }

  @Test
  public void instanceRelationshipsCount() {
    assertCount(instanceRelationshipsUrl("?limit=100"), "instanceRelationships", 5);
  }

  private JsonObject get(URL url) {
    try {
      CompletableFuture<Response> getCompleted = new CompletableFuture<>();
      client.get(url, TENANT_ID, json(getCompleted));
      Response response = getCompleted.get(5, SECONDS);
      assertThat(response.getStatusCode(), is(HTTP_OK));
      return response.getJson();
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(url + ": " + e.getMessage(), e);
    }
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

  private JsonObject getInstanceRelationship(String id) {
    try {
      CompletableFuture<Response> getCompleted = new CompletableFuture<>();
      client.get(instanceRelationshipsUrl("?limit=100"), TENANT_ID, json(getCompleted));
      Response response = getCompleted.get(5, SECONDS);
      JsonObject body = response.getJson();
      JsonArray array = body.getJsonArray("instanceRelationships");
      for (int i=0; i<array.size(); i++) {
        JsonObject ir = array.getJsonObject(i);
        if (id.equals(ir.getString("id"))) {
          return ir;
        }
      }
      throw new AssertionFailedError("instance relationship " + id + " not found: " + body);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(id + ": " + e.getMessage(), e);
    }
  }

  @Test
  public void instanceRelationshipGlobalAfrica2() throws Exception {
    JsonObject ir = getInstanceRelationship("e5cea7b1-3c48-428c-bc5e-2efc9ead1924");
    assertThat(ir.getString("superInstanceId"), is("f7e82a1e-fc06-4b82-bb1d-da326cb378ce"));
    assertThat(ir.getString("subInstanceId"), is("04489a01-f3cd-4f9e-9be4-d9c198703f45"));
    assertThat(ir.getString("instanceRelationshipTypeId"), is("30773a27-b485-4dab-aeb6-b8c04fa3cb17"));
    assertMetadata(ir);
  }
}
