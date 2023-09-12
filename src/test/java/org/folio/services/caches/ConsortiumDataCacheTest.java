package org.folio.services.caches;

import static com.github.tomakehurst.wiremock.client.WireMock.get;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.folio.okapi.common.XOkapiHeaders;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class ConsortiumDataCacheTest {

  @ClassRule
  public static WireMockRule mockServer = new WireMockRule(WireMockConfiguration.wireMockConfig()
    .notifier(new ConsoleNotifier(false))
    .dynamicPort());

  private static final String TENANT_ID = "diku";
  private static final String USER_TENANTS_PATH = "/user-tenants?limit=1";
  private static final String USER_TENANTS_FIELD = "userTenants";
  private static final String CENTRAL_TENANT_ID_FIELD = "centralTenantId";
  private static final String CONSORTIUM_ID_FIELD = "consortiumId";

  private final Vertx vertx = Vertx.vertx();
  private ConsortiumDataCache consortiumDataCache;
  private Map<String, String> okapiHeaders;

  @Before
  public void setUp() {
    consortiumDataCache = new ConsortiumDataCache(vertx, vertx.createHttpClient());
    okapiHeaders = Map.of(
      XOkapiHeaders.TENANT, TENANT_ID,
      XOkapiHeaders.TOKEN, "token",
      XOkapiHeaders.URL, mockServer.baseUrl());
  }

  @Test
  public void shouldReturnConsortiumData(TestContext context) {
    Async async = context.async();
    String expectedCentralTenantId = "mobius";
    String expectedConsortiumId = UUID.randomUUID().toString();

    JsonObject userTenantsCollection = new JsonObject()
      .put(USER_TENANTS_FIELD, new JsonArray()
        .add(new JsonObject()
          .put(CENTRAL_TENANT_ID_FIELD, expectedCentralTenantId)
          .put(CONSORTIUM_ID_FIELD, expectedConsortiumId)));

    WireMock.stubFor(get(USER_TENANTS_PATH)
      .willReturn(WireMock.ok().withBody(userTenantsCollection.encodePrettily())));

    Future<Optional<ConsortiumData>> future = consortiumDataCache.getConsortiumData(TENANT_ID, okapiHeaders);

    future.onComplete(ar -> {
      context.assertTrue(ar.succeeded());
      context.assertTrue(ar.result().isPresent());
      ConsortiumData consortiumData = ar.result().get();
      context.assertEquals(expectedCentralTenantId, consortiumData.getCentralTenantId());
      context.assertEquals(expectedConsortiumId, consortiumData.getConsortiumId());
      async.complete();
    });
  }

  @Test
  public void shouldReturnEmptyOptionalIfSpecifiedTenantInHeadersIsNotInConsortium(TestContext context) {
    Async async = context.async();
    JsonObject emptyUserTenantsCollection = new JsonObject()
      .put(USER_TENANTS_FIELD, JsonArray.of());

    WireMock.stubFor(get(USER_TENANTS_PATH)
      .willReturn(WireMock.ok().withBody(emptyUserTenantsCollection.encodePrettily())));

    Future<Optional<ConsortiumData>> future = consortiumDataCache.getConsortiumData(TENANT_ID, okapiHeaders);

    future.onComplete(ar -> {
      context.assertTrue(ar.succeeded());
      context.assertTrue(ar.result().isEmpty());
      async.complete();
    });
  }

  @Test
  public void shouldReturnFailedFutureWhenGetServerErrorOnConsortiumDataLoading(TestContext context) {
    Async async = context.async();
    WireMock.stubFor(get(USER_TENANTS_PATH).willReturn(WireMock.serverError()));

    Future<Optional<ConsortiumData>> future = consortiumDataCache.getConsortiumData(TENANT_ID, okapiHeaders)
      .onComplete(context.asyncAssertFailure());

    future.onComplete(ar -> {
      context.assertTrue(ar.failed());
      async.complete();
    });
  }

  @Test
  public void shouldReturnFailedFutureWhenSpecifiedTenantIdIsNull(TestContext context) {
    Async async = context.async();
    WireMock.stubFor(get(USER_TENANTS_PATH).willReturn(WireMock.serverError()));

    Future<Optional<ConsortiumData>> future = consortiumDataCache.getConsortiumData(null, okapiHeaders)
      .onComplete(context.asyncAssertFailure());

    future.onComplete(ar -> {
      context.assertTrue(ar.failed());
      async.complete();
    });
  }
}
