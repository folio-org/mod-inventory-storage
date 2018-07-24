package org.folio.rest.api;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import static org.folio.rest.support.http.InterfaceUrls.servicePointsUsersUrl;
import org.junit.Assert;

/**
 *
 * @author kurt
 */
public class ServicePointsUserTest {
  private static Logger logger = LoggerFactory.getLogger(ServicePointsUserTest.class);
  private static final String SUPPORTED_CONTENT_TYPE_JSON_DEF = "application/json";
  
  public void beforeEach()
      throws InterruptedException, ExecutionException, TimeoutException,
      MalformedURLException {
    StorageTestSuite.deleteAll(servicePointsUsersUrl(""));
  }
  
  private static void send(URL url, HttpMethod method, String content,
                    String contentType, Handler<HttpClientResponse> handler) {

    HttpClient client = StorageTestSuite.getVertx().createHttpClient();
    HttpClientRequest request;

    if(content == null){
      content = "";
    }
    Buffer buffer = Buffer.buffer(content);

    if (method == HttpMethod.POST) {
      request = client.postAbs(url.toString());
    }
    else if (method == HttpMethod.DELETE) {
      request = client.deleteAbs(url.toString());
    }
    else if (method == HttpMethod.GET) {
      request = client.getAbs(url.toString());
    }
    else {
      request = client.putAbs(url.toString());
    }
    request.exceptionHandler(error -> {
      Assert.fail(error.getLocalizedMessage());
    })
    .handler(handler);

    request.putHeader("Authorization", "test_tenant");
    request.putHeader("x-okapi-tenant", "test_tenant");
    request.putHeader("Accept", "application/json,text/plain");
    request.putHeader("Content-type", contentType);
    request.end(buffer);
  }
  
  public static Response createServicePointUser(UUID id,
      UUID userId, List<UUID> servicePointIds, UUID defaultServicePointId) 
      throws MalformedURLException {
    JsonObject request = new JsonObject();
    if(id != null) { request.put("id", id.toString()); }
    request.put("userId", userId.toString());
    if(defaultServicePointId != null) {
      request.put("defaultServicePointId", defaultServicePointId.toString()); 
    }
    if(servicePointIds != null && !servicePointIds.isEmpty()) {
      JsonArray spIds = new JsonArray();
      for(UUID uuid : servicePointIds) {
        spIds.add(uuid.toString());
      }
      request.put("servicePointIds", spIds);
    }
    CompletableFuture<Response> createServicePointUser = new CompletableFuture<>();
    send(servicePointsUsersUrl(""), HttpMethod.POST, request.toString(),
        SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(createServicePointUser));
    return createServicePointUser.get(5, TimeUnit.SECONDS);
  }
  
}
