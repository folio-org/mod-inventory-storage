package org.folio.rest;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
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
    PostgresClient.setIsEmbedded(true);
    PostgresClient.getInstance(vertx).startEmbeddedPostgres();
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
   
  @Test
  public void exampleTest(TestContext context) {
    context.assertTrue(true);
  }
    /* 
  @Test
  public void getDataTest(TestContext context) throws MalformedURLException {

    Async async = context.async();
    HttpClient client = vertx.createHttpClient();

    String url = String.format("/item_storage/item/%s","79");
    URL getItemUrl = new URL("http", "localhost", port, url);

    System.out.println("Getting Item");
    System.out.println(getItemUrl);

    HttpClientRequest request = client.getAbs(getItemUrl.toString(), response -> {

	    final int statusCode = response.statusCode();                    
	    response.bodyHandler(new Handler<Buffer>() {
		    @Override
		    public void handle(Buffer body) {

			JsonObject restResponse = new JsonObject(body.getString(0,body.length()));

			System.out.println("Response Received");
			System.out.println("REST RESPONSE: " + restResponse.toString());

			context.assertEquals(restResponse.getString("title") , "Refactoring");
			async.complete();
			
			}
		});
	});

    request.headers().add("Accept","application/json");
    request.end();
  }
    
    
@Test
  public void postDataTest(TestContext context) throws MalformedURLException {

    Async async = context.async();
    UUID id = UUID.randomUUID();
    HttpClient client = vertx.createHttpClient();

    URL postItemUrl = new URL("http", "localhost", port, "/item_storage/item");

    System.out.println("Posting Item");
    System.out.println(postItemUrl);

    JsonObject post = new JsonObject();
    post.put("id","79");
    post.put("title", "Refactoring");
    post.put("barcode", "31415");
    post.put("instance_id", "MadeUp");
    
    HttpClientRequest request = client.postAbs(postItemUrl.toString(), response -> {

	    final int statusCode = response.statusCode();                    
	    response.bodyHandler(new Handler<Buffer>() {
		    @Override
		    public void handle(Buffer body) {

			JsonObject restResponse = new JsonObject(body.getString(0,body.length()));

			System.out.println("Response Received");
			System.out.println(restResponse.toString());

			context.assertEquals(restResponse.getString("title") , "Refactoring");
			async.complete();
			
			}
		});
	});

    request.headers().add("Accept","application/json");
    request.headers().add("Content-type","application/json");
    request.end(post.encodePrettily());
 
  }
    */
    
  @Test
  public void postGetDataTest(TestContext context) throws MalformedURLException {

    System.out.println("POST GET DATA TEST");
    Async async = context.async();
    String uuid = UUID.randomUUID().toString();
    HttpClient client = vertx.createHttpClient();

    URL postItemUrl = new URL("http", "localhost", port, "/item_storage/item");

    System.out.println("Posting Item");
    System.out.println(postItemUrl);

    JsonObject post = new JsonObject();
    post.put("id", uuid);
    post.put("title", "Refactoring");
    post.put("barcode", "31415");
    post.put("instance_id", "MadeUp");
    
    HttpClientRequest postRequest = client.postAbs(postItemUrl.toString(), response -> {

	    final int statusCode = response.statusCode();                    
	    response.bodyHandler(new Handler<Buffer>() {
		    @Override
		    public void handle(Buffer body) {

			JsonObject restResponse = new JsonObject(body.getString(0,body.length()));

			System.out.println("POST Response Received");
			System.out.println(restResponse.toString());

			context.assertEquals(restResponse.getString("title") , "Refactoring");
			
			String id = restResponse.getString("id");
			String url = String.format("/item_storage/item/%s",id);
			
			System.out.println("Id received " + restResponse.getString("id"));
		 
			 try{
			     URL getItemUrl = new URL("http", "localhost", port, url);

			     System.out.println("Getting Item");
			     System.out.println(getItemUrl);

			     HttpClientRequest getRequest = client.getAbs(getItemUrl.toString(), response -> {

				     final int statusCode = response.statusCode();                    
				     response.bodyHandler(new Handler<Buffer>() {
					     @Override
					     public void handle(Buffer body) {

						 JsonObject restResponse = new JsonObject(body.getString(0,body.length()));

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
    
}
