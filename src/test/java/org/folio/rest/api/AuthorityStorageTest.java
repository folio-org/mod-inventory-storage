package org.folio.rest.api;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.rest.support.http.InterfaceUrls.authoritiesStorageUrl;
import static org.folio.rest.support.matchers.DomainEventAssertions.assertCreateEventForAuthority;
import static org.folio.rest.support.matchers.DomainEventAssertions.assertRemoveAllEventForAuthority;
import static org.folio.rest.support.matchers.DomainEventAssertions.assertRemoveEventForAuthority;
import static org.folio.rest.support.matchers.DomainEventAssertions.assertUpdateEventForAuthority;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.folio.utility.VertxUtility.getClient;
import static org.junit.Assert.assertEquals;

import io.vertx.core.json.JsonObject;
import java.net.HttpURLConnection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import junitparams.JUnitParamsRunner;
import lombok.SneakyThrows;
import org.folio.rest.jaxrs.model.Authority;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class AuthorityStorageTest extends TestBase {

  @SneakyThrows
  @Before
  public void beforeEach() {
    clearData();
    StorageTestSuite.deleteAll(authoritiesStorageUrl(""));

    removeAllEvents();
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
    assertCreateEventForAuthority(response2.getJson());
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
    assertRemoveAllEventForAuthority();
  }

  @Test
  public void deleteById() {
    assertEquals(0, authoritiesClient.getAll().size());
    createAuthRecords(1);
    var response = authoritiesClient.getAll();
    assertEquals(1, response.size());
    authoritiesClient.delete(UUID.fromString(response.get(0).getString("id")));
    var response2 = authoritiesClient.getAll();
    assertEquals(0, response2.size());
    assertRemoveEventForAuthority(response.get(0));
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
    getClient().delete(authoritiesStorageUrl("/" + UUID.randomUUID()), TENANT_ID, response2);
    Response deleteResponse = deleteCompleted.get(10, TimeUnit.SECONDS);
    assertEquals(HttpURLConnection.HTTP_NOT_FOUND, deleteResponse.getStatusCode());
    assertEquals(1, response.size());
  }

  @Test
  public void putById() {
    assertEquals(0, authoritiesClient.getAll().size());
    createAuthRecords(1);

    // Clear Kafka events after create to reduce chances of
    // CREATE messages appearing after UPDATE later on.
    // This should be removed once the messaging problem is
    // properly resolved.
    removeAllEvents();

    var response = authoritiesClient.getAll();
    assertEquals(1, response.size());
    JsonObject object = new JsonObject(response.get(0).encode());
    object.put("personalName", "changed");
    authoritiesClient.replace(UUID.fromString(object.getString("id")), object);
    var response2 = authoritiesClient.getById(UUID.fromString(response.get(0).getString("id")));
    assertEquals(object.getString("personalName"), response2.getJson().getString("personalName"));
    assertUpdateEventForAuthority(response.get(0), response2.getJson());
  }

  @Test
  public void optimisticLockingVersion() {
    assertEquals(0, authoritiesClient.getAll().size());
    authoritiesClient.create(new JsonObject()
      .put("personalName", "personalName")
      .put("_version", 1));
    var response = authoritiesClient.getAll();
    assertEquals(1, response.size());
    JsonObject authority = new JsonObject(response.get(0).encode());

    authority.put("personalName", "changed");
    // updating with current _version 1 succeeds and increments _version to 2
    assertEquals(204, put(authority).getStatusCode());

    authority.put("personalName", "changed one more time");
    // updating with outdated _version 1 fails, current _version is 2
    assertEquals(409, put(authority).getStatusCode());
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
    getClient().put(authoritiesStorageUrl("/" + UUID.randomUUID()), object.mapTo(Authority.class), TENANT_ID, response2);
    Response putResponse = putCompleted.get(10, TimeUnit.SECONDS);
    assertEquals(HttpURLConnection.HTTP_NOT_FOUND, putResponse.getStatusCode());
    var response3 = authoritiesClient.getById(UUID.fromString(response.get(0).getString("id")));
    assertEquals("personalName0", response3.getJson().getString("personalName"));
  }

  @Test(expected = AssertionError.class)
  public void postWithWrongFields() {
    assertEquals(0, authoritiesClient.getAll().size());

    authoritiesClient.create(new JsonObject()
      .put("personalName", "personalName")
      .put("wrong", "test"));
  }

  private void createAuthRecords(int quantity) {
    for (int i = 0; i < quantity; i++) {
      authoritiesClient.create(new JsonObject().put("personalName", "personalName" + i));
    }
  }

  private Response put(JsonObject object) {
    final UUID id = UUID.fromString(object.getString("id"));
    CompletableFuture<Response> replaceCompleted = new CompletableFuture<>();

    getClient().put(authoritiesStorageUrl(String.format("/%s", id)), object,
      TENANT_ID, ResponseHandler.empty(replaceCompleted));

    try {
      return replaceCompleted.get(10, SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

}
