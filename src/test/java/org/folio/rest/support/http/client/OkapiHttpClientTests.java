package org.folio.rest.support.http.client;

import static com.github.tomakehurst.wiremock.client.WireMock.created;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.HttpStatus.HTTP_NO_CONTENT;
import static org.folio.HttpStatus.HTTP_OK;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.vertx.core.json.JsonObject;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import lombok.SneakyThrows;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

public class OkapiHttpClientTests {
  private static VertxAssistant vertxAssistant;

  @Rule
  public WireMockRule fakeWebServer = new WireMockRule(wireMockConfig()
    .dynamicPort());
  private final URL okapiUrl = new URL("http://okapi.com");
  private final String tenantId = "test-tenant";
  private final String token = "eyJhbGciOiJIUzI1NiJ9"
    + ".eyJzdWIiOiJkaWt1X2FkbWluIiwidXNlcl9pZCI6ImFhMjZjYjg4LTc2YjEtNTQ1OS1hMjM1LWZjYTRmZDI3MGMyMyIs"
    + "ImlhdCI6MTU3NjAxMzY3MiwidGVuYW50IjoiZGlrdSJ9.oGCb0gDIdkXGlCiECvJHgQMXD3QKKW2vTh7PPCrpds8";
  private final String userId = "aa26cb88-76b1-5459-a235-fca4fd270c23";
  private final String requestId = "test-request-id";

  public OkapiHttpClientTests() throws MalformedURLException { }

  @BeforeClass
  public static void beforeAll() {
    vertxAssistant = new VertxAssistant();

    vertxAssistant.start();
  }

  @AfterClass
  public static void afterAll() {
    if (vertxAssistant != null) {
      vertxAssistant.stop();
    }
  }

  @SneakyThrows
  @Test
  public void canPostWithJson() {
    final String locationResponseHeader = "/a-different-location";

    fakeWebServer.stubFor(matchingFolioHeaders(post(urlPathEqualTo("/record")))
      .withHeader("Content-Type", equalTo("application/json"))
      .withRequestBody(equalToJson(dummyJsonRequestBody().encodePrettily()))
      .willReturn(created().withBody(dummyJsonResponseBody())
        .withHeader("Content-Type", "application/json")
        .withHeader("Location", locationResponseHeader)));

    OkapiHttpClient client = createClient();

    final var postCompleted = client.post(
      fakeWebServer.url("/record"), dummyJsonRequestBody());

    final var response = postCompleted.toCompletableFuture().get(2, SECONDS);

    assertThat(response.getStatusCode(), is(HTTP_CREATED.toInt()));
    assertThat(response.getJson().getString("message"), is("hello"));
    assertThat(response.getContentType(), is("application/json"));
    assertThat(response.getLocation(), is(locationResponseHeader));

  }

  @Test
  public void canGetJson()
    throws InterruptedException, ExecutionException, TimeoutException {

    final String locationResponseHeader = "/a-different-location";

    fakeWebServer.stubFor(matchingFolioHeaders(get(urlPathEqualTo("/record")))
      .willReturn(okJson(dummyJsonResponseBody())
        .withHeader("Location", locationResponseHeader)));

    OkapiHttpClient client = createClient();

    final var getCompleted = client.get(fakeWebServer.url("/record"));

    final Response response = getCompleted.toCompletableFuture().get(2, SECONDS);

    assertThat(response.getStatusCode(), is(HTTP_OK.toInt()));
    assertThat(response.getJson().getString("message"), is("hello"));
    assertThat(response.getContentType(), is("application/json"));
    assertThat(response.getLocation(), is(locationResponseHeader));
  }

  @Test
  public void canPutWithJson()
    throws InterruptedException, ExecutionException, TimeoutException {

    fakeWebServer.stubFor(
      matchingFolioHeaders(put(urlPathEqualTo("/record/12345")))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(equalToJson(dummyJsonRequestBody().encodePrettily()))
        .willReturn(noContent()));

    OkapiHttpClient client = createClient();

    final var postCompleted = client.put(
      fakeWebServer.url("/record/12345"), dummyJsonRequestBody());

    final Response response = postCompleted.toCompletableFuture().get(2, SECONDS);

    assertThat(response.getStatusCode(), is(HTTP_NO_CONTENT.toInt()));
    assertThat(response.getBody(), is(emptyOrNullString()));
  }

  @Test
  public void canDelete()
    throws InterruptedException, ExecutionException, TimeoutException {

    fakeWebServer.stubFor(matchingFolioHeaders(delete(urlPathEqualTo("/record")))
      .willReturn(noContent()));

    OkapiHttpClient client = createClient();

    final var deleteCompleted = client.delete(
      fakeWebServer.url("/record"));

    final Response response = deleteCompleted.toCompletableFuture().get(2, SECONDS);

    assertThat(response.getStatusCode(), is(HTTP_NO_CONTENT.toInt()));
    assertThat(response.getBody(), is(emptyOrNullString()));
  }

  //TODO: Maybe replace this with a filter extension
  private MappingBuilder matchingFolioHeaders(MappingBuilder mappingBuilder) {
    return mappingBuilder
      .withHeader("X-Okapi-Url", equalTo(okapiUrl.toString()))
      .withHeader("X-Okapi-Tenant", equalTo(tenantId))
      .withHeader("X-Okapi-Token", equalTo(token))
      .withHeader("X-Okapi-User-Id", equalTo(userId))
      .withHeader("X-Okapi-Request-Id", equalTo(requestId));
  }

  private OkapiHttpClient createClient() {
    return new OkapiHttpClient(vertxAssistant.getVertx(),
      okapiUrl, tenantId, token, userId, requestId, error -> {});
  }

  private JsonObject dummyJsonRequestBody() {
    return new JsonObject().put("from", "James");
  }

  private String dummyJsonResponseBody() {
    return new JsonObject().put("message", "hello")
      .encodePrettily();
  }
}
