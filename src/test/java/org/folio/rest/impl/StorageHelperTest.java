package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.core.Response;

import org.folio.rest.api.StorageTestSuite;
import org.folio.rest.api.TestBase;
import org.folio.rest.impl.StorageHelper;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.resource.ItemStorageResource;
import org.folio.rest.jaxrs.resource.ItemStorageResource.DeleteItemStorageItemsByItemIdResponse;
import org.folio.rest.jaxrs.resource.ItemStorageResource.GetItemStorageItemsByItemIdResponse;
import org.folio.rest.jaxrs.resource.ItemStorageResource.PostItemStorageItemsResponse;
import org.folio.rest.jaxrs.resource.ItemStorageResource.PutItemStorageItemsByItemIdResponse;
import org.folio.rest.testing.UtilityClassTester;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class StorageHelperTest extends TestBase {
  private Map<String,String> okapiHeaders =
      Collections.singletonMap("x-okapi-tenant", StorageTestSuite.TENANT_ID);

  @Rule
  public Timeout timeoutRule = Timeout.seconds(5);

  @Rule
  public RunTestOnContext contextRule = new RunTestOnContext();

  private String randomUuid() {
    return UUID.randomUUID().toString();
  }

  private Handler<AsyncResult<Response>> asyncAssert(TestContext testContext, int httpStatus, String snippet) {
    Async async = testContext.async();
    return handler -> {
      testContext.assertTrue(handler.succeeded());
      Response response = handler.result();
      testContext.assertEquals(httpStatus, response.getStatus());
      String entity = "null";
      if (response.getEntity() != null) {
        entity = response.getEntity().toString();
      }
      testContext.assertTrue(entity.contains(snippet),
          "'" + snippet + "' expected in error message: " + entity);
      async.complete();
    };
  }

  @Test
  public void isUtilityClass() {
    UtilityClassTester.assertUtilityClass(StorageHelper.class);
  }

  @Test
  public void deleteByIdInternalError(TestContext testContext) {
    StorageHelper.deleteById("item", "invalidid", okapiHeaders, Vertx.currentContext(),
        asyncAssert(testContext, 500, "invalidid"),
        DeleteItemStorageItemsByItemIdResponse::withNoContent,
        DeleteItemStorageItemsByItemIdResponse::withPlainInternalServerError);
  }

  @Test
  public void getByIdInvalidUuid(TestContext testContext) {
    StorageHelper.getById("item", Item.class, "invalidUuid", okapiHeaders, Vertx.currentContext(),
        asyncAssert(testContext, 500, "Invalid UUID string: invalidUuid"),
        null,
        null,
        GetItemStorageItemsByItemIdResponse::withPlainInternalServerError);
  }

  @Test
  public void getByIdPostgresError(TestContext testContext) {
    StorageHelper.getById("doesnotexist", Item.class, randomUuid(), okapiHeaders, Vertx.currentContext(),
        asyncAssert(testContext, 500, "doesnotexist"),
        null,
        null,
        GetItemStorageItemsByItemIdResponse::withPlainInternalServerError);
  }

  @Test
  public void getByIdNotFound(TestContext testContext) {
    StorageHelper.getById("item", Item.class, randomUuid(), okapiHeaders, Vertx.currentContext(),
        asyncAssert(testContext, 404, "Not found"),
        null,
        ItemStorageResource.GetItemStorageItemsByItemIdResponse::withPlainNotFound,
        null);
  }

  @Test
  public void postException(TestContext testContext) {
    StorageHelper.post("item", "string", okapiHeaders, Vertx.currentContext(),
        asyncAssert(testContext, 500, "NoSuchMethodException: java.lang.String.getId"),
        null,
        null,
        PostItemStorageItemsResponse::withPlainInternalServerError);
  }

  @Test
  public void postPostgresError(TestContext testContext) {
    StorageHelper.post("doesnotexist", new Item(), okapiHeaders, Vertx.currentContext(),
        asyncAssert(testContext, 500, "doesnotexist"),
        null,
        null,
        PostItemStorageItemsResponse::withPlainInternalServerError);
  }

  @Test
  public void putException(TestContext testContext) {
    StorageHelper.put("item", "string", randomUuid(), okapiHeaders, Vertx.currentContext(),
        asyncAssert(testContext, 500, "NoSuchMethodException: java.lang.String.setId"),
        null,
        null,
        PutItemStorageItemsByItemIdResponse::withPlainInternalServerError);
  }

  @Test
  public void putPostgresError(TestContext testContext) {
    StorageHelper.put("doesnotexist", new Item(), randomUuid(), okapiHeaders, Vertx.currentContext(),
        asyncAssert(testContext, 500, "doesnotexist"),
        null,
        null,
        PutItemStorageItemsByItemIdResponse::withPlainInternalServerError);
  }

}
