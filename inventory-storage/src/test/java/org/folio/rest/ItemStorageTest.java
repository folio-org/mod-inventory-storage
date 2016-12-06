package org.folio.rest;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.rest.persist.PostgresClient;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpClientRequest;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import io.vertx.ext.unit.Async;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.Handler;
import java.util.UUID;

@RunWith(VertxUnitRunner.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ItemStorageTest {

  private static Vertx vertx;
  private static int port;
  private static ArrayList<String> urls;

  private static void setupPostgres() throws Exception {
//    PostgresClient.setIsEmbedded(true);
//    PostgresClient.getInstance(vertx).startEmbeddedPostgres();
  }

  @BeforeClass
  public static void before(TestContext context) throws InterruptedException, ExecutionException, TimeoutException {
    vertx = Vertx.vertx();

    // find a free port and use it to deploy the verticle
    port = NetworkUtils.nextFreePort();

    try {
      setupPostgres();
    } catch (Exception e) {
      context.fail(e);
    }

    DeploymentOptions options = new DeploymentOptions().setConfig(new JsonObject().put("http.port", port));
    vertx.deployVerticle(RestVerticle.class.getName(), options);

    // TODO change this to wait with handler
    //wait until until the port the verticle is deployed on starts responding
    for (int i = 0; i < 15; i++) {
      try {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress("localhost", port), 100 /* ms timeout */);
        socket.close();
        break;
      } catch (IOException e) { // NOSONAR
        // loop for 15 seconds while waiting for the verticle to deploy
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e1) {}
      }
    }

    Async async = context.async(1);
    PostgresClient.getInstance(vertx).mutate(
        "CREATE SCHEMA test; create table test.item (_id SERIAL PRIMARY KEY,jsonb JSONB NOT NULL)",
        res -> {
          if(res.succeeded()){
            async.countDown();
            System.out.println("item table created");
          }
          else{
            System.out.println("item table NOT created");
            Assert.fail("item table NOT created " + res.cause().getMessage());
            async.complete();
          }
});
  }

  @AfterClass
  public static void after() {
    vertx.close();
    // another dirty hack - loop for 15 seconds while waiting for the port the verticle was deployed on
    // stops answering - meaning the verticle is no longer listening on thaat port and hence un-deployed
    for (int i = 0; i < 15; i++) {
      try {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress("localhost", port), 100 /* ms timeout */);
        socket.close();
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e1) {}
      } catch (IOException e) { // NOSONAR
        break;
      }
    }

    deleteTempFilesCreated();
  }

  private static void deleteTempFilesCreated(){
    System.out.println("deleting created files");
    // Lists all files in folder
    File folder = new File(RestVerticle.DEFAULT_TEMP_DIR);
    File fList[] = folder.listFiles();
    // Searchs items_flat.txt
    for (int i = 0; i < fList.length; i++) {
      String pes = fList[i].getName();
      if (pes.endsWith("items_flat.txt")) {
        // and deletes
        boolean success = fList[i].delete();
      }
    }
  }

  @Before
  public void beforeEach() throws InterruptedException, ExecutionException, TimeoutException {
    CompletableFuture recordTableTruncated = new CompletableFuture();

    PostgresClient.getInstance(vertx).mutate(
      "TRUNCATE TABLE test.item;",
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

			JsonObject restResponse = new JsonObject(body.getString(0,body.length()));

			context.assertEquals(statusCode, HttpURLConnection.HTTP_CREATED);
			System.out.println("RECORD CREATED");
			System.out.println(restResponse.toString());

			context.assertEquals(restResponse.getString("title") , "Real Analysis");
			async.complete();

			}
		});
	});

    request.headers().add("Accept","application/json");
    request.headers().add("Content-type","application/json");
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

    HttpClientRequest postRequest = client.postAbs(postItemUrl.toString(), response -> {

	     final int postStatusCode = response.statusCode();
	    response.bodyHandler(new Handler<Buffer>() {
		    @Override
		    public void handle(Buffer body) {

			JsonObject restResponse = new JsonObject(body.getString(0,body.length()));

			context.assertEquals(postStatusCode, HttpURLConnection.HTTP_CREATED);
			System.out.println("POST Response Received");
			System.out.println(restResponse.toString());

			context.assertEquals(restResponse.getString("title") , "Refactoring");

			String id = restResponse.getString("id");
			String url = String.format("/item-storage/item/%s",id);

			System.out.println("Id received " + restResponse.getString("id"));

			 try{
			     URL getItemUrl = new URL("http", "localhost", port, url);

			     System.out.println("Getting Item");
			     System.out.println(getItemUrl);

			     HttpClientRequest getRequest = client.getAbs(getItemUrl.toString(), response -> {

				     final int getStatusCode = response.statusCode();
				     response.bodyHandler(new Handler<Buffer>() {
					     @Override
					     public void handle(Buffer body) {

						 JsonObject restResponse = new JsonObject(body.getString(0,body.length()));

						 context.assertEquals(getStatusCode, HttpURLConnection.HTTP_OK);
						 System.out.println("GET Response Received");
						 System.out.println(restResponse.toString());

						 context.assertEquals(restResponse.getString("id") , id);
						 async.complete();

					     }
					 });
				 });

			     getRequest.headers().add("Accept","application/json");
			     getRequest.end();

			 }catch(MalformedURLException ex){
			     System.out.println("EXCEPTION HAPPENED");
			 }
		    }
		});
	});

    postRequest.headers().add("Accept","application/json");
    postRequest.headers().add("Content-type","application/json");
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

      HttpClientRequest post1Request = client.postAbs(itemsUrl.toString(), post1Response -> {

	      final int post1StatusCode = post1Response.statusCode();
	      context.assertEquals(post1StatusCode, HttpURLConnection.HTTP_CREATED);
	      System.out.println("POST 1 CREATED");
	      HttpClientRequest post2Request = client.postAbs(itemsUrl.toString(), post2Response -> {

		      final int post2StatusCode = post1Response.statusCode();
		      context.assertEquals(post2StatusCode, HttpURLConnection.HTTP_CREATED);
		      System.out.println("POST 2 CREATED");
		      HttpClientRequest request = client.getAbs(itemsUrl.toString(), getResponse -> {

			      final int getAllStatusCode  = getResponse.statusCode();
			      context.assertEquals(getAllStatusCode, HttpURLConnection.HTTP_OK);
			      System.out.println("GET ALL OK");
			      getResponse.bodyHandler( new Handler<Buffer>() {
				      @Override
				      public void handle(Buffer body) {

					  JsonObject restResponse = new JsonObject(body.getString(0, body.length()));

					  System.out.println(restResponse.toString());
					  context.assertEquals(restResponse.getInteger("total_records"), 2);

					  JsonObject item1 = restResponse.getJsonArray("items").getJsonObject(0);
					  System.out.println("POST 1: " + post1);
					  System.out.println("ITEM 1: " + item1);
					  context.assertEquals(item1.getString("title"), post1.getString("title"));

					  JsonObject item2 = restResponse.getJsonArray("items").getJsonObject(1);
					  System.out.println("POST 2: " + post2);
					  System.out.println("ITEM 2: " + item2);
					  context.assertEquals(item2.getString("title"), post2.getString("title"));
					  async.complete();
				      }
				  });
			  });

		      request.headers().add("Accept", "application/json");
		      request.end();

		  });

	      post2Request.headers().add("Accept","application/json");
	      post2Request.headers().add("Content-type","application/json");
	      post2Request.end(post2.encodePrettily());

	  });

      post1Request.headers().add("Accept","application/json");
      post1Request.headers().add("Content-type","application/json");
      post1Request.end(post1.encodePrettily());
  }

}

