package org.folio.rest.api;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.folio.rest.support.http.InterfaceUrls.itemDamagedStatusesUrl;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.net.URL;
import java.util.UUID;

import org.folio.HttpStatus;
import org.folio.rest.impl.ItemDamagedStatusAPI;
import org.folio.rest.jaxrs.model.ItemDamageStatus;
import org.junit.Before;
import org.junit.Test;

import io.vertx.core.json.JsonObject;

public class ItemDamagedStatusAPITest extends TestBase {

  public static final String TEST_TENANT = "test_tenant";

  @Before
  public void beforeEach() {
    StorageTestSuite.deleteAll(TEST_TENANT, ItemDamagedStatusAPI.REFERENCE_TABLE);
  }

  @Test
  public void canCreateAnItemDamagedStatus() {
    ItemDamageStatus status = new ItemDamageStatus()
      .withName("test item damaged status name")
      .withSource("local");

    client.post(itemDamagedStatusesUrl(EMPTY), status, TEST_TENANT)
      .whenComplete((response, throwable) -> {
        assertThat(response, notNullValue());
        assertThat(response.getStatusCode(), is(HttpStatus.HTTP_CREATED.toInt()));
        JsonObject statusJson = response.getJson();
        assertThat(statusJson.getString("id"), notNullValue());
        assertThat(statusJson.getString("name"), is(status.getName()));
      });
  }

  @Test
  public void shouldNotCreateItemDamagedStatusWhenUnexpectedPropertiesAreProvided() {
    JsonObject object = new JsonObject().put("foo", "boo");
    client.post(itemDamagedStatusesUrl(EMPTY), object, TEST_TENANT)
      .whenComplete((response, throwable) -> {
        assertThat(response, notNullValue());
        assertThat(response.getStatusCode(), is(422));
      });
  }

  @Test
  public void shouldNotBeAbleToCreateItemDamagedStatusWithTheSameName() {
    ItemDamageStatus status = new ItemDamageStatus()
      .withName("test item damaged status name")
      .withSource("local");

    client.post(itemDamagedStatusesUrl(EMPTY), status, TEST_TENANT)
      .whenComplete((response, throwable) -> {
        assertThat(response, notNullValue());
        assertThat(response.getStatusCode(), is(HttpStatus.HTTP_CREATED.toInt()));
      });

    client.post(itemDamagedStatusesUrl(EMPTY), status, TEST_TENANT)
      .whenComplete((response, throwable) -> {
        assertThat(response, notNullValue());
        assertThat(response.getStatusCode(), is(HttpStatus.HTTP_BAD_REQUEST.toInt()));
      });
  }

  @Test
  public void shouldNotBeAbleToCreateItemDamagedStatusWithTheSameId() {
    ItemDamageStatus status = new ItemDamageStatus()
      .withName("test item damaged status name")
      .withSource("local");

    client.post(itemDamagedStatusesUrl(EMPTY), status, TEST_TENANT)
      .whenComplete((response, throwable) -> {
        assertThat(response, notNullValue());
        assertThat(response.getStatusCode(), is(HttpStatus.HTTP_CREATED.toInt()));
      })
      .thenCompose(response -> {
        status.setId(response.getJson().getString("id"));
        return client.post(itemDamagedStatusesUrl(EMPTY), status, TEST_TENANT);
      })
      .whenComplete((response, throwable) -> {
        assertThat(response, notNullValue());
        assertThat(response.getStatusCode(), is(HttpStatus.HTTP_BAD_REQUEST.toInt()));
      });
  }

  @Test
  public void canGetItemDamagedStatus() {
    ItemDamageStatus status = new ItemDamageStatus()
      .withName("test item damaged status name")
      .withSource("local");

    client.post(itemDamagedStatusesUrl(EMPTY), status, TEST_TENANT)
      .whenComplete((response, throwable) -> {
        assertThat(response, notNullValue());
        assertThat(response.getStatusCode(), is(HttpStatus.HTTP_CREATED.toInt()));
      })
      .thenCompose(response -> {
        String id = response.getJson().getString("id");
        return client.get(itemDamagedStatusesUrl("/" + id), TEST_TENANT);
      })
      .whenComplete((response, throwable) -> {
        assertThat(response, notNullValue());
        assertThat(response.getStatusCode(), is(HttpStatus.HTTP_OK.toInt()));
        JsonObject statusJson = response.getJson();
        assertThat(statusJson.getString("id"), notNullValue());
        assertThat(statusJson.getString("name"), is(status.getName()));
      });
  }

  @Test
  public void canGetAllItemDamagedStatuses() {
    ItemDamageStatus status = new ItemDamageStatus()
      .withName("test item damaged status name")
      .withSource("local");

    client.post(itemDamagedStatusesUrl(EMPTY), status, TEST_TENANT)
      .whenComplete((response, throwable) -> {
        assertThat(response, notNullValue());
        assertThat(response.getStatusCode(), is(HttpStatus.HTTP_CREATED.toInt()));
      })
      .thenCompose(response -> {
        status.setName("second test item damaged status name");
        return client.post(itemDamagedStatusesUrl(EMPTY), status, TEST_TENANT);
      })
      .whenComplete((response, throwable) -> {
        assertThat(response, notNullValue());
        assertThat(response.getStatusCode(), is(HttpStatus.HTTP_CREATED.toInt()));
      })
      .thenCompose(it -> client.get(itemDamagedStatusesUrl(EMPTY), TEST_TENANT))
      .whenComplete((response, throwable) -> {
        int size = response.getJson().getJsonArray("itemDamageStatuses").size();
        assertThat(size, is(2));
      });
  }

  @Test
  public void shouldFindAnItemDamagedStatusByName() {
    ItemDamageStatus status = new ItemDamageStatus()
      .withName("test item damaged status name")
      .withSource("local");

    client.post(itemDamagedStatusesUrl(EMPTY), status, TEST_TENANT)
      .whenComplete((response, throwable) -> {
        assertThat(response, notNullValue());
        assertThat(response.getStatusCode(), is(HttpStatus.HTTP_CREATED.toInt()));
      })
      .thenCompose(response -> {
        status.setName("itemDamagedStatusName");
        return client.post(itemDamagedStatusesUrl(EMPTY), status, TEST_TENANT);
      })
      .whenComplete((response, throwable) -> {
        assertThat(response, notNullValue());
        assertThat(response.getStatusCode(), is(HttpStatus.HTTP_CREATED.toInt()));
      })
      .thenCompose(it -> {
        URL url = itemDamagedStatusesUrl("?query=name=itemDamagedStatusName");
        return client.get(url, TEST_TENANT);
      })
      .whenComplete((response, throwable) -> {
        JsonObject body = response.getJson();
        int size = body.getJsonArray("itemDamageStatuses").size();
        assertThat(size, is(1));
        JsonObject statuses = body.getJsonArray("itemDamageStatuses").getJsonObject(0);
        assertThat(statuses.getString("name"), is("itemDamagedStatusName"));
      });
  }

  @Test
  public void canDeleteItemDamagedStatus() {
    ItemDamageStatus status = new ItemDamageStatus()
      .withName("test item damaged status name")
      .withSource("local");

    client.post(itemDamagedStatusesUrl(EMPTY), status, TEST_TENANT)
      .whenComplete((response, throwable) -> {
        assertThat(response, notNullValue());
        assertThat(response.getStatusCode(), is(HttpStatus.HTTP_CREATED.toInt()));
      })
      .thenCompose(response -> {
        String id = response.getJson().getString("id");
        return client.delete(itemDamagedStatusesUrl("/" + id), TEST_TENANT);
      })
      .whenComplete((response, throwable) -> {
        assertThat(response, notNullValue());
        assertThat(response.getStatusCode(), is(HttpStatus.HTTP_NO_CONTENT.toInt()));
      });
  }

  @Test
  public void cannotDeleteItemDamagedStatusThatDoesNotExist() {
    client.delete(itemDamagedStatusesUrl("/" + UUID.randomUUID()), TEST_TENANT)
      .whenComplete((response, throwable) -> {
        assertThat(response, notNullValue());
        assertThat(response.getStatusCode(), is(HttpStatus.HTTP_NOT_FOUND.toInt()));
      });
  }

  @Test
  public void canUpdateItemDamagedStatus() {
    ItemDamageStatus status = new ItemDamageStatus()
      .withName("test item damaged status name")
      .withSource("local");

    client.post(itemDamagedStatusesUrl(EMPTY), status, TEST_TENANT)
      .whenComplete((response, throwable) -> {
        assertThat(response, notNullValue());
        assertThat(response.getStatusCode(), is(HttpStatus.HTTP_CREATED.toInt()));
      })
      .thenCompose(response -> {
        status.setSource("folio");
        return client.put(itemDamagedStatusesUrl(EMPTY), status, TEST_TENANT);
      })
      .whenComplete((response, throwable) -> {
        assertThat(response, notNullValue());
        assertThat(response.getStatusCode(), is(HttpStatus.HTTP_NO_CONTENT.toInt()));
      });
  }

  @Test
  public void cannotUpdateItemDamagedStatusThatDoesNotExist() {
    ItemDamageStatus status = new ItemDamageStatus()
      .withName("test item damaged status name")
      .withSource("local");

    client.put(itemDamagedStatusesUrl(EMPTY), status, TEST_TENANT)
      .whenComplete((response, throwable) -> {
        assertThat(response, notNullValue());
        assertThat(response.getStatusCode(), is(HttpStatus.HTTP_NOT_FOUND.toInt()));
      });
  }
}
