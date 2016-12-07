package org.folio.rest;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.rest.jaxrs.model.Item;
import org.junit.*;
import org.junit.runner.RunWith;

import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.rest.persist.PostgresClient;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import io.vertx.ext.unit.Async;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.Handler;
import java.util.UUID;

@RunWith(VertxUnitRunner.class)
public class ItemStorageTest {

  private static Vertx vertx;
  private static int port;

  private static final String TENANT_ID = "test";
  private static final String TENANT_HEADER = "X-Okapi-Tenant";

  @BeforeClass
  public static void before(TestContext context) throws InterruptedException, ExecutionException, TimeoutException {
    vertx = Vertx.vertx();

    port = NetworkUtils.nextFreePort();

    DeploymentOptions options = new DeploymentOptions();

    options.setConfig(new JsonObject().put("http.port", port));
    options.setWorker(true);

    startVerticle(options);
  }

  private static void startVerticle(DeploymentOptions options)
    throws InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture deploymentComplete = new CompletableFuture<String>();

    vertx.deployVerticle(RestVerticle.class.getName(), options, res -> {
      if(res.succeeded()) {
        deploymentComplete.complete(res.result());
      }
      else {
        deploymentComplete.completeExceptionally(res.cause());
      }
    });

    deploymentComplete.get(20000, TimeUnit.MILLISECONDS);
  }

  @AfterClass
  public static void after()
    throws InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture undeploymentComplete = new CompletableFuture<String>();

    vertx.close(res -> {
      if(res.succeeded()) {
        undeploymentComplete.complete(null);
      }
      else {
        undeploymentComplete.completeExceptionally(res.cause());
      }
    });

    undeploymentComplete.get(20000, TimeUnit.MILLISECONDS);
  }

  @Before
  public void beforeEach() throws InterruptedException, ExecutionException, TimeoutException {
    CompletableFuture recordTableTruncated = new CompletableFuture();

    PostgresClient.getInstance(vertx).mutate(
      "TRUNCATE TABLE item;",
      res -> {
        if(res.succeeded()){
          recordTableTruncated.complete(null);
        }
        else{
          recordTableTruncated.completeExceptionally(res.cause());
        }
      });

    recordTableTruncated.get(5, TimeUnit.SECONDS);
  }

  @Test
  public void postDataTest(TestContext context) throws MalformedURLException {

    Async async = context.async();
    UUID id = UUID.randomUUID();
    HttpClient client = vertx.createHttpClient();

    URL postItemUrl = new URL("http", "localhost", port, "/item-storage/item");

    System.out.println("Posting Item");
    System.out.println(postItemUrl);

    JsonObject post = new JsonObject();
    post.put("id",id.toString());
    post.put("instance_id", "Fake");
    post.put("title", "Real Analysis");
    post.put("barcode", "271828");

    HttpClientRequest request = client.postAbs(postItemUrl.toString(), response -> {

	    final int statusCode = response.statusCode();

      response.bodyHandler(new Handler<Buffer>() {
		    @Override
		    public void handle(Buffer body) {

          JsonObject restResponse = new JsonObject(
            body.getString(0,body.length()));

          context.assertEquals(statusCode, HttpURLConnection.HTTP_CREATED);
          System.out.println("RECORD CREATED");
          System.out.println(restResponse.toString());

          context.assertEquals(restResponse.getString("title") ,
            "Real Analysis");

          async.complete();
			  }
		  });
	  });

    request.headers().add("Accept","application/json");
    request.headers().add("Content-type","application/json");
    request.headers().add(TENANT_HEADER, TENANT_ID);
    request.end(post.encodePrettily());
  }

  @Test
  public void postGetDataTest(TestContext context) throws MalformedURLException {

    Async async = context.async();
    String uuid = UUID.randomUUID().toString();
    HttpClient client = vertx.createHttpClient();

    URL postItemUrl = new URL("http", "localhost", port, "/item-storage/item");

    System.out.println("Posting Item");
    System.out.println(postItemUrl);

    JsonObject post = new JsonObject();
    post.put("id", uuid);
    post.put("instance_id", "MadeUp");
    post.put("title", "Refactoring");
    post.put("barcode", "314159");

    HttpClientRequest postRequest = client.postAbs(postItemUrl.toString(),
      response -> {
        final int postStatusCode = response.statusCode();
	      response.bodyHandler(body -> {

          JsonObject restResponse = new JsonObject(
            body.getString(0,body.length()));

          context.assertEquals(postStatusCode, HttpURLConnection.HTTP_CREATED);
          System.out.println("POST Response Received");
          System.out.println(restResponse.toString());

          context.assertEquals(restResponse.getString("title") , "Refactoring");

          String id = restResponse.getString("id");
          String url = String.format("/item-storage/item/%s",id);

          System.out.println("Id received " + restResponse.getString("id"));

          try {
             URL getItemUrl = new URL("http", "localhost", port, url);

             System.out.println("Getting Item");
             System.out.println(getItemUrl);

             HttpClientRequest getRequest = client.getAbs(
              getItemUrl.toString(), getResponse -> {

              final int getStatusCode = getResponse.statusCode();

              getResponse.bodyHandler(body1 -> {
                JsonObject restResponse1 =
                new JsonObject(body1.getString(0, body1.length()));

                context.assertEquals(getStatusCode, HttpURLConnection.HTTP_OK);
                System.out.println("GET Response Received");
                System.out.println(restResponse1.toString());

                context.assertEquals(restResponse1.getString("id") , id);
                async.complete();
               });
            });

            getRequest.headers().add("Accept","application/json");
            getRequest.headers().add(TENANT_HEADER, TENANT_ID);
            getRequest.end();
          }
          catch(MalformedURLException ex){
            System.out.println("EXCEPTION HAPPENED");
          }
        });
      });

    postRequest.headers().add("Accept","application/json");
    postRequest.headers().add("Content-type","application/json");
    postRequest.headers().add(TENANT_HEADER, TENANT_ID);
    postRequest.end(post.encodePrettily());

  }

  @Test
  public void GetAllTest(TestContext context) throws MalformedURLException {

    Async async = context.async();
    HttpClient client = vertx.createHttpClient();
    String uuid1 = UUID.randomUUID().toString();
    String uuid2 = UUID.randomUUID().toString();
    URL itemsUrl = new URL("http", "localhost", port, "/item-storage/item");

    JsonObject post1 = new JsonObject();
    post1.put("id", uuid1);
    post1.put("instance_id", "MadeUp");
    post1.put("title", "Refactoring");
    post1.put("barcode", "314159");

    JsonObject post2 = new JsonObject();
    post2.put("id", uuid2);
    post2.put("instance_id", "Fake");
    post2.put("title", "Real Analysis");
    post2.put("barcode", "271828");

    HttpClientRequest post1Request = client.postAbs(itemsUrl.toString(),
      post1Response -> {
        final int post1StatusCode = post1Response.statusCode();
        context.assertEquals(post1StatusCode, HttpURLConnection.HTTP_CREATED);
        System.out.println("POST 1 CREATED");

	      HttpClientRequest post2Request = client.postAbs(itemsUrl.toString(),
          post2Response -> {

		      final int post2StatusCode = post1Response.statusCode();
		      context.assertEquals(post2StatusCode, HttpURLConnection.HTTP_CREATED);
		      System.out.println("POST 2 CREATED");

          HttpClientRequest request = client.getAbs(itemsUrl.toString(),
            getResponse -> {

			      final int getAllStatusCode  = getResponse.statusCode();
			      context.assertEquals(getAllStatusCode, HttpURLConnection.HTTP_OK);
			      System.out.println("GET ALL OK");

			      getResponse.bodyHandler(body -> {
              JsonObject restResponse
                = new JsonObject(body.getString(0, body.length()));

            System.out.println(restResponse.toString());
            context.assertEquals(restResponse.getInteger("total_records"), 2);

            JsonObject item1 = restResponse.getJsonArray("items")
              .getJsonObject(0);

            System.out.println("POST 1: " + post1);
            System.out.println("ITEM 1: " + item1);

            context.assertEquals(item1.getString("title"),
              post1.getString("title"));

            JsonObject item2 = restResponse.getJsonArray("items").getJsonObject(1);
            System.out.println("POST 2: " + post2);
            System.out.println("ITEM 2: " + item2);
            context.assertEquals(item2.getString("title"), post2.getString("title"));
            async.complete();
          });
			  });

        request.headers().add("Accept", "application/json");
        request.headers().add(TENANT_HEADER, TENANT_ID);
        request.end();
		  });

      post2Request.headers().add("Accept","application/json");
      post2Request.headers().add("Content-type","application/json");
      post2Request.headers().add(TENANT_HEADER, TENANT_ID);
      post2Request.end(post2.encodePrettily());
	  });

    post1Request.headers().add("Accept","application/json");
    post1Request.headers().add("Content-type","application/json");
    post1Request.headers().add(TENANT_HEADER, TENANT_ID);
    post1Request.end(post1.encodePrettily());
  }

  @Test
  public void tenantIsRequiredForCreatingNewItem(TestContext context)
    throws MalformedURLException {

    Async async = context.async();

    Item item = new Item();
    item.setId(UUID.randomUUID().toString());
    item.setTitle("Refactoring");
    item.setInstanceId(UUID.randomUUID().toString());
    item.setBarcode("4554345453");

    Post(vertx, new URL("http", "localhost",  port, "/item-storage/item"),
      item, response -> {
        context.assertEquals(response.statusCode(), 400);

        response.bodyHandler( buffer -> {
          String responseBody = buffer.getString(0, buffer.length());

          context.assertEquals(responseBody, "Tenant Must Be Provided");

          async.complete();
        });
      });
  }

  @Test
  public void tenantIsRequiredForGettingAnItem(TestContext context)
    throws MalformedURLException {

    Async async = context.async();

    String path = String.format("/item-storage/item/%s",
      UUID.randomUUID().toString());

    URL getItemUrl = new URL("http", "localhost", port, path);

    Get(vertx, getItemUrl,
      response -> {
        context.assertEquals(response.statusCode(), 400);

        response.bodyHandler( buffer -> {
          String responseBody = buffer.getString(0, buffer.length());

          context.assertEquals(responseBody, "Tenant Must Be Provided");

          async.complete();
        });
      });
  }

  @Test
  public void tenantIsRequiredForGettingAllItems(TestContext context)
    throws MalformedURLException {

    Async async = context.async();

    String path = String.format("/item-storage/item");

    URL getItemsUrl = new URL("http", "localhost", port, path);

    Get(vertx, getItemsUrl,
      response -> {
        System.out.println("Response received");

        context.assertEquals(response.statusCode(), 400);

        response.bodyHandler( buffer -> {
          String responseBody = buffer.getString(0, buffer.length());

          context.assertEquals(responseBody, "Tenant Must Be Provided");

          async.complete();
        });
      });
  }

  public void Post(Vertx vertx,
                   URL url,
                   Object body,
                   Handler<HttpClientResponse> responseHandler) {

    HttpClient client = vertx.createHttpClient();

    HttpClientRequest request = client.postAbs(url.toString(), responseHandler);

    request.headers().add("Accept","application/json");
    request.headers().add("Content-type","application/json");
    request.end(Json.encodePrettily(body));
  }

  public void Get(Vertx vertx,
                   URL url,
                   Handler<HttpClientResponse> responseHandler) {

    HttpClient client = vertx.createHttpClient();

    HttpClientRequest request = client.getAbs(url.toString(), responseHandler);

    request.headers().add("Accept","application/json");
    request.end();
  }
}
