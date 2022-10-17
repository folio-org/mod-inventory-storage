package org.folio.rest.api;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.folio.rest.support.http.InterfaceUrls.itemDamagedStatusesUrl;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.net.URL;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.HttpStatus;
import org.folio.rest.impl.ItemDamagedStatusAPI;
import org.folio.rest.jaxrs.model.ItemDamageStatus;
import org.folio.rest.support.Response;
import org.junit.Before;
import org.junit.Test;

import io.vertx.core.json.JsonObject;

public class ItemDamagedStatusAPITest extends TestBase {
  public static final String TEST_TENANT = "test_tenant";

  @Before
  public void beforeEach() {
    assertTrue(StorageTestSuite.deleteAll(TEST_TENANT,
        ItemDamagedStatusAPI.REFERENCE_TABLE));
  }

  @Test
  public void canCreateAnItemDamagedStatus()
    throws InterruptedException, ExecutionException, TimeoutException {

    ItemDamageStatus status = new ItemDamageStatus()
      .withName("test item damaged status name")
      .withSource("local");

    Response result = client.post(itemDamagedStatusesUrl(EMPTY), status, TEST_TENANT)
      .get(5, TimeUnit.SECONDS);

    assertThat(result, notNullValue());
    assertThat(result.getStatusCode(), is(HttpStatus.HTTP_CREATED.toInt()));
    JsonObject statusJson = result.getJson();
    assertThat(statusJson.getString("id"), notNullValue());
    assertThat(statusJson.getString("name"), is(status.getName()));
  }

  @Test
  public void shouldNotCreateItemDamagedStatusWhenUnexpectedPropertiesAreProvided()
    throws InterruptedException, ExecutionException, TimeoutException {

    JsonObject object = new JsonObject().put("foo", "boo");

    Response result = client.post(itemDamagedStatusesUrl(EMPTY), object, TEST_TENANT)
      .get(5, TimeUnit.SECONDS);

    assertThat(result, notNullValue());
    assertThat(result.getStatusCode(), is(422));
  }

  @Test
  public void shouldNotBeAbleToCreateItemDamagedStatusWithTheSameName()
    throws InterruptedException, ExecutionException, TimeoutException {

    ItemDamageStatus status = new ItemDamageStatus()
      .withName("test item damaged status name")
      .withSource("local");

    Response result = client.post(itemDamagedStatusesUrl(EMPTY), status, TEST_TENANT)
      .get(5, TimeUnit.SECONDS);

    assertThat(result, notNullValue());
    assertThat(result.getStatusCode(), is(HttpStatus.HTTP_CREATED.toInt()));

    result = client.post(itemDamagedStatusesUrl(EMPTY), status, TEST_TENANT)
      .get(5, TimeUnit.SECONDS);

    assertThat(result, notNullValue());
    assertThat(result.getStatusCode(), is(HttpStatus.HTTP_BAD_REQUEST.toInt()));
  }

  @Test
  public void shouldNotBeAbleToCreateItemDamagedStatusWithTheSameId()
    throws InterruptedException, ExecutionException, TimeoutException {

    ItemDamageStatus status = new ItemDamageStatus()
      .withName("test item damaged status name")
      .withSource("local");

    Response result = client.post(itemDamagedStatusesUrl(EMPTY), status, TEST_TENANT)
      .whenComplete(ItemDamagedStatusAPITest::assertHttpStatusIsCreated)
      .thenCompose(response -> {
        status.setId(response.getJson().getString("id"));
        return client.post(itemDamagedStatusesUrl(EMPTY), status, TEST_TENANT);
      })
      .get(5, TimeUnit.SECONDS);


    assertThat(result, notNullValue());
    assertThat(result.getStatusCode(), is(HttpStatus.HTTP_BAD_REQUEST.toInt()));
  }

  @Test
  public void canGetItemDamagedStatus()
    throws InterruptedException, ExecutionException, TimeoutException {

    ItemDamageStatus status = new ItemDamageStatus()
      .withName("test item damaged status name")
      .withSource("local");

    Response result = client.post(itemDamagedStatusesUrl(EMPTY), status, TEST_TENANT)
      .whenComplete(ItemDamagedStatusAPITest::assertHttpStatusIsCreated)
      .thenCompose(response -> {
        String id = response.getJson().getString("id");
        return client.get(itemDamagedStatusesUrl("/" + id), TEST_TENANT);
      })
      .get(5, TimeUnit.SECONDS);

    assertThat(result, notNullValue());
    assertThat(result.getStatusCode(), is(HttpStatus.HTTP_OK.toInt()));
    JsonObject statusJson = result.getJson();
    assertThat(statusJson.getString("id"), notNullValue());
    assertThat(statusJson.getString("name"), is(status.getName()));
  }

  @Test
  public void canGetAllItemDamagedStatuses()
    throws InterruptedException, ExecutionException, TimeoutException {

    URL url = itemDamagedStatusesUrl(EMPTY);

    ItemDamageStatus status = new ItemDamageStatus()
      .withName("test item damaged status name")
      .withSource("local");

    Response result = client.post(url, status, TEST_TENANT)
      .whenComplete(ItemDamagedStatusAPITest::assertHttpStatusIsCreated)
      .thenCompose(it -> client.post(url, status.withName("name"), TEST_TENANT))
      .whenComplete(ItemDamagedStatusAPITest::assertHttpStatusIsCreated)
      .thenCompose(it -> client.get(url, TEST_TENANT))
      .get(5, TimeUnit.SECONDS);

    int size = result.getJson().getJsonArray("itemDamageStatuses").size();
    assertThat(size, is(2));
  }

  @Test
  public void shouldFindAnItemDamagedStatusByName()
    throws InterruptedException, ExecutionException, TimeoutException {

    ItemDamageStatus status = new ItemDamageStatus()
      .withName("test item damaged status name")
      .withSource("local");

    URL baseUrl = itemDamagedStatusesUrl(EMPTY);

    Response result = client.post(baseUrl, status, TEST_TENANT)
      .whenComplete(ItemDamagedStatusAPITest::assertHttpStatusIsCreated)
      .thenCompose(it -> client.post(baseUrl, status.withName("targetName"), TEST_TENANT))
      .whenComplete(ItemDamagedStatusAPITest::assertHttpStatusIsCreated)
      .thenApply(it -> itemDamagedStatusesUrl("?query=name=targetName"))
      .thenCompose(url -> client.get(url, TEST_TENANT))
      .get(5, TimeUnit.SECONDS);

    JsonObject body = result.getJson();
    int size = body.getJsonArray("itemDamageStatuses").size();
    assertThat(size, is(1));
    JsonObject statuses = body.getJsonArray("itemDamageStatuses").getJsonObject(0);
    assertThat(statuses.getString("name"), is("targetName"));
  }

  @Test
  public void canDeleteItemDamagedStatus()
    throws InterruptedException, ExecutionException, TimeoutException {

    ItemDamageStatus status = new ItemDamageStatus()
      .withName("test item damaged status name")
      .withSource("local");

    Response result = client.post(itemDamagedStatusesUrl(EMPTY), status, TEST_TENANT)
      .whenComplete(ItemDamagedStatusAPITest::assertHttpStatusIsCreated)
      .thenApply(response -> response.getJson().getString("id"))
      .thenCompose(id -> client.delete(itemDamagedStatusesUrl("/" + id), TEST_TENANT))
      .get(5, TimeUnit.SECONDS);

    assertThat(result, notNullValue());
    assertThat(result.getStatusCode(), is(HttpStatus.HTTP_NO_CONTENT.toInt()));
  }

  @Test
  public void cannotDeleteItemDamagedStatusThatDoesNotExist()
    throws InterruptedException, ExecutionException, TimeoutException {

    URL url = itemDamagedStatusesUrl("/" + UUID.randomUUID());
    Response result = client.delete(url, TEST_TENANT).get(5, TimeUnit.SECONDS);

    assertThat(result, notNullValue());
    assertThat(result.getStatusCode(), is(HttpStatus.HTTP_NOT_FOUND.toInt()));
  }

  @Test
  public void canUpdateItemDamagedStatus()
    throws InterruptedException, ExecutionException, TimeoutException {

    ItemDamageStatus status = new ItemDamageStatus()
      .withName("test item damaged status name")
      .withSource("local");


    Response result = client.post(itemDamagedStatusesUrl(EMPTY), status, TEST_TENANT)
      .whenComplete(ItemDamagedStatusAPITest::assertHttpStatusIsCreated)
      .thenApply(response -> status.withId(response.getJson().getString("id")))
      .thenApply(it -> itemDamagedStatusesUrl("/" + status.getId()))
      .thenCompose(url -> client.put(url, status.withSource("folio"), TEST_TENANT))
      .get(5, TimeUnit.SECONDS);

    assertThat(result, notNullValue());
    assertThat(result.getStatusCode(), is(HttpStatus.HTTP_NO_CONTENT.toInt()));
  }

  @Test
  public void cannotUpdateItemDamagedStatusThatDoesNotExist()
    throws InterruptedException, ExecutionException, TimeoutException {

    ItemDamageStatus status = new ItemDamageStatus()
      .withName("test item damaged status name")
      .withSource("local");

    URL url = itemDamagedStatusesUrl("/" + UUID.randomUUID());
    Response result = client.put(url, status, TEST_TENANT).get(5, TimeUnit.SECONDS);

    assertThat(result, notNullValue());
    assertThat(result.getStatusCode(), is(HttpStatus.HTTP_NOT_FOUND.toInt()));
  }

  private static void assertHttpStatusIsCreated(Response response, Throwable throwable) {
    assertThat(response, notNullValue());
    assertThat(response.getStatusCode(), is(HttpStatus.HTTP_CREATED.toInt()));
  }
}
