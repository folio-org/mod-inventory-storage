package org.folio.rest.api;

import static io.vertx.core.http.HttpMethod.DELETE;
import static io.vertx.core.http.HttpMethod.GET;
import static io.vertx.core.http.HttpMethod.POST;
import static io.vertx.core.http.HttpMethod.PUT;
import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.folio.rest.support.http.InterfaceUrls.itemDamagedStatusesUrl;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.net.MalformedURLException;
import java.util.UUID;

import org.folio.rest.impl.ItemDamagedStatusAPI;
import org.folio.rest.jaxrs.model.ItemDamageStatus;
import org.junit.Before;
import org.junit.Test;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.json.JsonObject;

public class ItemDamagedStatusAPITest extends TestBase {

  @Before
  public void beforeEach() {
    StorageTestSuite.deleteAll("test_tenant", ItemDamagedStatusAPI.REFERENCE_TABLE);
  }

  @Test
  public void canCreateItemDamagedStatus()
    throws MalformedURLException {

    ItemDamageStatus status = new ItemDamageStatus()
      .withName("test item damaged status name")
      .withSource("local");

    JsonObject response = send(
      itemDamagedStatusesUrl(""),
      POST,
      JsonObject.mapFrom(status).encode(),
      HTTP_CREATED
    );

    assertThat(response.getString("id"), notNullValue());
    assertThat(response.getString("name"), is(status.getName()));
  }

  @Test
  public void shouldNotCreateItemDamagedStatusWhenUnprocessableEntity()
    throws MalformedURLException {

    send(itemDamagedStatusesUrl(""),
      POST,
      new JsonObject().put("foo", "boo").encode(),
      HttpResponseStatus.UNPROCESSABLE_ENTITY.code()
    );
  }

  @Test
  public void shouldNotCreateItemDamagedStatusWithTheSameName()
    throws MalformedURLException {

    ItemDamageStatus status = new ItemDamageStatus()
      .withName("test item damaged status name")
      .withSource("local");

    send(
      itemDamagedStatusesUrl(""),
      POST,
      JsonObject.mapFrom(status).encode(),
      HTTP_CREATED
    );

    send(
      itemDamagedStatusesUrl(""),
      POST,
      JsonObject.mapFrom(status).encode(),
      HTTP_BAD_REQUEST
    );
  }

  @Test
  public void shouldNotCreateItemDamagedStatusWithTheSameId()
    throws MalformedURLException {

    ItemDamageStatus status = new ItemDamageStatus()
      .withName("test item damaged status name")
      .withSource("local");

    JsonObject createdItemDamagedStatusResponse = send(
      itemDamagedStatusesUrl(""),
      POST,
      JsonObject.mapFrom(status).encode(),
      HTTP_CREATED
    );

    String id = createdItemDamagedStatusResponse.getString("id");

    JsonObject result = send(
      itemDamagedStatusesUrl(""),
      POST,
      JsonObject.mapFrom(status.withId(id)).encode(),
      HTTP_BAD_REQUEST
    );
  }

  @Test
  public void canGetItemDamagedStatus()
    throws MalformedURLException {

    ItemDamageStatus status = new ItemDamageStatus()
      .withName("test item damaged status name")
      .withSource("local");

    JsonObject createdItemDamagedStatusResponse = send(
      itemDamagedStatusesUrl(""),
      POST,
      JsonObject.mapFrom(status).encode(),
      HTTP_CREATED
    );

    String id = createdItemDamagedStatusResponse.getString("id");
    JsonObject result = send(
      itemDamagedStatusesUrl("/" + id),
      GET,
      null,
      HTTP_OK
    );

    assertThat(result, notNullValue());
    assertThat(result.getString("id"), notNullValue());
    assertThat(result.getString("name"), is(status.getName()));
  }

  @Test
  public void canGetAllItemDamagedStatuses() throws MalformedURLException {
    ItemDamageStatus status = new ItemDamageStatus()
      .withName("test item damaged status name")
      .withSource("local");

    send(itemDamagedStatusesUrl(""),
      POST,
      JsonObject.mapFrom(status).encode(),
      HTTP_CREATED
    );

    send(itemDamagedStatusesUrl(""),
      POST,
      JsonObject.mapFrom(status.withName("second test item damaged status name")).encode(),
      HTTP_CREATED
    );

    JsonObject result = send(itemDamagedStatusesUrl(""), GET, null, HTTP_OK);
    assertThat(result.getJsonArray("itemDamageStatuses").size(), is(2));
  }

  @Test
  public void shouldFindItemDamagedStatusesByName() throws MalformedURLException {
    ItemDamageStatus status = new ItemDamageStatus()
      .withName("test item damaged status name")
      .withSource("local");

    send(itemDamagedStatusesUrl(""),
      POST,
      JsonObject.mapFrom(status).encode(),
      HTTP_CREATED
    );

    send(itemDamagedStatusesUrl(""),
      POST,
      JsonObject.mapFrom(status.withName("itemDamagedStatusName")).encode(),
      HTTP_CREATED
    );

    JsonObject result = send(
      itemDamagedStatusesUrl("?query=name=itemDamagedStatusName"),
      GET,
      null,
      HTTP_OK);

    assertThat(result.getJsonArray("itemDamageStatuses").size(), is(1));
    JsonObject itemDamageStatuses = result.getJsonArray("itemDamageStatuses").getJsonObject(0);
    assertThat(itemDamageStatuses.getString("name"), is("itemDamagedStatusName"));
  }

  @Test
  public void canDeleteItemDamagedStatus() throws MalformedURLException {
    ItemDamageStatus status = new ItemDamageStatus()
      .withName("test item damaged status name")
      .withSource("local");

    JsonObject createdItemDamagedStatusResponse = send(
      itemDamagedStatusesUrl(""),
      POST,
      JsonObject.mapFrom(status).encode(),
      HTTP_CREATED
    );

    String id = createdItemDamagedStatusResponse.getString("id");

    send(itemDamagedStatusesUrl("/" + id), DELETE, null, HTTP_NO_CONTENT);
  }

  @Test
  public void cannotDeleteItemDamagedStatusThatDoesNotExist()
    throws MalformedURLException {

    send(itemDamagedStatusesUrl("/" + UUID.randomUUID()), DELETE, null, HTTP_NOT_FOUND);
  }

  @Test
  public void canUpdateItemDamagedStatus() throws MalformedURLException {
    ItemDamageStatus status = new ItemDamageStatus()
      .withName("test item damaged status name")
      .withSource("local");

    JsonObject createdItemDamagedStatusResponse = send(
      itemDamagedStatusesUrl(""),
      POST,
      JsonObject.mapFrom(status).encode(),
      HTTP_CREATED
    );

    String id = createdItemDamagedStatusResponse.getString("id");

    send(itemDamagedStatusesUrl("/" + id),
      PUT,
      JsonObject.mapFrom(status.withSource("folio")).encode(),
      HTTP_NO_CONTENT);
  }

  @Test
  public void cannotUpdateItemDamagedStatusThatDoesNotExist()
    throws MalformedURLException {
    ItemDamageStatus status = new ItemDamageStatus()
      .withName("test item damaged status name")
      .withSource("local");
    send(itemDamagedStatusesUrl("/" + UUID.randomUUID()),
      DELETE,
      JsonObject.mapFrom(status).encode(),
      HTTP_NOT_FOUND);
  }

}
