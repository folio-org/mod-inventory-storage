package org.folio.rest.api;

import io.vertx.core.json.JsonObject;
import junitparams.JUnitParamsRunner;
import lombok.SneakyThrows;
import org.folio.rest.jaxrs.model.Authority;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.HttpURLConnection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.folio.rest.api.StorageTestSuite.TENANT_ID;
import static org.folio.rest.support.http.InterfaceUrls.authoritiesStorageUrl;
import static org.junit.Assert.assertEquals;

@RunWith(JUnitParamsRunner.class)
public class AuthorityStorageTest extends TestBase {

  @Before
  public void beforeEach() {
    StorageTestSuite.deleteAll(authoritiesStorageUrl(""));
  }

  @Test
  public void getAllRecords() {
    assertEquals(0, authoritiesClient.getAll().size());
    createAuthRecords(2);
    var response = authoritiesClient.getAll();
    assertEquals(2, response.size());
    assertEquals("personalName0", response.get(0).getString("personalName"));
  }

  @Test
  public void getById() {
    assertEquals(0, authoritiesClient.getAll().size());
    createAuthRecords(1);
    var response = authoritiesClient.getAll();
    assertEquals(1, response.size());
    var response2 = authoritiesClient.getById(UUID.fromString(response.get(0).getString("id")));
    assertEquals("personalName0", response2.getJson().getString("personalName"));
  }

  @Test
  public void getByIdNotFound() {
    createAuthRecords(1);
    var response = authoritiesClient.getById(UUID.randomUUID());
    assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getStatusCode());
  }

  @Test
  public void deleteAll() {
    assertEquals(0, authoritiesClient.getAll().size());
    createAuthRecords(2);
    var response = authoritiesClient.getAll();
    assertEquals(2, response.size());
    authoritiesClient.deleteAll();
    response = authoritiesClient.getAll();
    assertEquals(0, response.size());
  }

  @Test
  public void deleteById() {
    assertEquals(0, authoritiesClient.getAll().size());
    createAuthRecords(1);
    var response = authoritiesClient.getAll();
    assertEquals(1, response.size());
    authoritiesClient.delete(UUID.fromString(response.get(0).getString("id")));
    response = authoritiesClient.getAll();
    assertEquals(0, response.size());
  }

  @Test
  @SneakyThrows
  public void deleteByIdNotFound() {
    assertEquals(0, authoritiesClient.getAll().size());
    createAuthRecords(1);
    var response = authoritiesClient.getAll();
    assertEquals(1, response.size());
    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();
    var response2 = ResponseHandler.empty(deleteCompleted);
    client.delete(authoritiesStorageUrl("/" + UUID.randomUUID()), TENANT_ID, response2);
    Response deleteResponse = deleteCompleted.get(5, TimeUnit.SECONDS);
    assertEquals(HttpURLConnection.HTTP_NOT_FOUND, deleteResponse.getStatusCode());
    assertEquals(1, response.size());
  }

  @Test
  public void putById() {
    assertEquals(0, authoritiesClient.getAll().size());
    createAuthRecords(1);
    var response = authoritiesClient.getAll();
    assertEquals(1, response.size());
    JsonObject object = response.get(0);
    object.put("personalName", "changed");
    authoritiesClient.replace(UUID.fromString(object.getString("id")), object);
    var response2 = authoritiesClient.getById(UUID.fromString(response.get(0).getString("id")));
    assertEquals(object.getString("personalName"), response2.getJson().getString("personalName"));
  }

  @Test
  @SneakyThrows
  public void putByIdNotFound() {
    assertEquals(0, authoritiesClient.getAll().size());
    createAuthRecords(1);
    var response = authoritiesClient.getAll();
    assertEquals(1, response.size());
    JsonObject object = response.get(0);
    object.put("personalName", "changed");
    CompletableFuture<Response> putCompleted = new CompletableFuture<>();
    var response2 = ResponseHandler.any(putCompleted);
    client.put(authoritiesStorageUrl("/" + UUID.randomUUID()), object.mapTo(Authority.class), TENANT_ID, response2);
    Response putResponse = putCompleted.get(5, TimeUnit.SECONDS);
    assertEquals(HttpURLConnection.HTTP_NOT_FOUND, putResponse.getStatusCode());
    var response3 = authoritiesClient.getById(UUID.fromString(response.get(0).getString("id")));
    assertEquals("personalName0", response3.getJson().getString("personalName"));
  }

  @Test(expected = AssertionError.class)
  public void postWithWrongFields() {
    assertEquals(0, authoritiesClient.getAll().size());

    var res = authoritiesClient.create(new JsonObject()
      .put("personalName", "personalName")
      .put("wrong", "test"));
  }

  private void createAuthRecords(int quantity) {
    for (int i = 0; i < quantity; i++) {
      authoritiesClient.create(new JsonObject().put("personalName", "personalName" + i));
    }
  }

}
