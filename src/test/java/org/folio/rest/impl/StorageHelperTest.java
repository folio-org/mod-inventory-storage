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
import org.folio.rest.jaxrs.resource.ItemStorage.DeleteItemStorageItemsByItemIdResponse;
import org.folio.rest.jaxrs.resource.ItemStorage.GetItemStorageItemsByItemIdResponse;
import org.folio.rest.jaxrs.resource.ItemStorage.PostItemStorageItemsResponse;
import org.folio.rest.jaxrs.resource.ItemStorage.PutItemStorageItemsByItemIdResponse;
import org.folio.rest.testing.UtilityClassTester;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class StorageHelperTest extends TestBase {
  @Rule
  public Timeout timeoutRule = Timeout.seconds(5);

  @Rule
  public RunTestOnContext contextRule = new RunTestOnContext();

  private Map<String,String> okapiHeaders =
      Collections.singletonMap("x-okapi-tenant", StorageTestSuite.TENANT_ID);

  private String randomUuid() {
    return UUID.randomUUID().toString();
  }

  /**
   * Return a handler that, when invoked, asserts that the AsyncResult succeeded, the Response has httpStatus,
   * and the toString() of the entity of Response contains the snippet.
   */
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
        DeleteItemStorageItemsByItemIdResponse.class,
        asyncAssert(testContext, 500, "invalidid"));
  }

  @Test
  public void getByIdInvalidUuid(TestContext testContext) {
    StorageHelper.getById("item", Item.class, "invalidUuid", okapiHeaders, Vertx.currentContext(),
        GetItemStorageItemsByItemIdResponse.class,
        asyncAssert(testContext, 500, "22P02"));
  }

  @Test
  public void getByIdPostgresError(TestContext testContext) {
    StorageHelper.getById("doesnotexist", Item.class, randomUuid(), okapiHeaders, Vertx.currentContext(),
        GetItemStorageItemsByItemIdResponse.class,
        asyncAssert(testContext, 500, "doesnotexist"));
  }

  @Test
  public void getByIdNotFound(TestContext testContext) {
    StorageHelper.getById("item", Item.class, randomUuid(), okapiHeaders, Vertx.currentContext(),
        GetItemStorageItemsByItemIdResponse.class,
        asyncAssert(testContext, 404, "Not found"));
  }

  @Test
  public void postException(TestContext testContext) {
    StorageHelper.post("item", "string", okapiHeaders, Vertx.currentContext(),
        PostItemStorageItemsResponse.class,
        asyncAssert(testContext, 500, "java.lang.String.getId"));
  }

  @Test
  public void postPostgresError(TestContext testContext) {
    StorageHelper.post("doesnotexist", new Item(), okapiHeaders, Vertx.currentContext(),
        PostItemStorageItemsResponse.class,
        asyncAssert(testContext, 500, "doesnotexist"));
  }

  @Test
  public void putException(TestContext testContext) {
    StorageHelper.put("item", "string", randomUuid(), okapiHeaders, Vertx.currentContext(),
        PutItemStorageItemsByItemIdResponse.class,
        asyncAssert(testContext, 500, "java.lang.String.setId"));
  }

  @Test
  public void putPostgresError(TestContext testContext) {
    StorageHelper.put("doesnotexist", new Item(), randomUuid(), okapiHeaders, Vertx.currentContext(),
        PutItemStorageItemsByItemIdResponse.class,
        asyncAssert(testContext, 500, "doesnotexist"));
  }

}
