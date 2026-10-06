package org.folio.services.caches;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.folio.dataimport.testsupport.rest.BaseWireMockTest;
import org.folio.okapi.common.XOkapiHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
class ConsortiumDataCacheTest extends BaseWireMockTest {

  private static final String TENANT_ID = "diku";
  private static final String USER_TENANTS_PATH = "/user-tenants?limit=1";
  private static final String USER_TENANTS_FIELD = "userTenants";
  private static final String ECS_TENANTS_FIELD = "tenants";
  private static final String CENTRAL_TENANT_ID_FIELD = "centralTenantId";
  private static final String CONSORTIUM_ID_FIELD = "consortiumId";

  private ConsortiumDataCache consortiumDataCache;
  private Map<String, String> okapiHeaders;

  @BeforeEach
  void setUp(Vertx vertx) {
    consortiumDataCache = new ConsortiumDataCache(vertx, vertx.createHttpClient());
    okapiHeaders = Map.of(
      XOkapiHeaders.TENANT, TENANT_ID,
      XOkapiHeaders.TOKEN, "token",
      XOkapiHeaders.URL, WIRE_MOCK.baseUrl());

    JsonObject emptyEcsTenantsCollection = new JsonObject()
      .put(ECS_TENANTS_FIELD, JsonArray.of());

    WIRE_MOCK.stubFor(get(urlMatching("/consortia/.*/tenants"))
      .willReturn(WireMock.ok().withBody(emptyEcsTenantsCollection.encodePrettily())));
  }

  @Test
  void shouldReturnConsortiumData(VertxTestContext context) {
    String expectedCentralTenantId = "mobius";
    String expectedConsortiumId = UUID.randomUUID().toString();

    JsonObject userTenantsCollection = new JsonObject()
      .put(USER_TENANTS_FIELD, new JsonArray()
        .add(new JsonObject()
          .put(CENTRAL_TENANT_ID_FIELD, expectedCentralTenantId)
          .put(CONSORTIUM_ID_FIELD, expectedConsortiumId)));

    WIRE_MOCK.stubFor(get(USER_TENANTS_PATH)
      .willReturn(WireMock.ok().withBody(userTenantsCollection.encodePrettily())));

    Future<Optional<ConsortiumData>> future = consortiumDataCache.getConsortiumData(TENANT_ID, okapiHeaders);

    future.onComplete(ar -> {
      context.verify(() -> {
        assertTrue(ar.succeeded());
        assertTrue(ar.result().isPresent());
        ConsortiumData consortiumData = ar.result().get();
        assertEquals(expectedCentralTenantId, consortiumData.centralTenantId());
        assertEquals(expectedConsortiumId, consortiumData.consortiumId());
      });
      context.completeNow();
    });
  }

  @Test
  void shouldReturnEmptyOptionalIfSpecifiedTenantInHeadersIsNotInConsortium(VertxTestContext context) {
    JsonObject emptyUserTenantsCollection = new JsonObject()
      .put(USER_TENANTS_FIELD, JsonArray.of());

    WIRE_MOCK.stubFor(get(USER_TENANTS_PATH)
      .willReturn(WireMock.ok().withBody(emptyUserTenantsCollection.encodePrettily())));

    Future<Optional<ConsortiumData>> future = consortiumDataCache.getConsortiumData(TENANT_ID, okapiHeaders);

    future.onComplete(ar -> {
      context.verify(() -> {
        assertTrue(ar.succeeded());
        assertTrue(ar.result().isEmpty());
      });
      context.completeNow();
    });
  }

  @Test
  void shouldReturnFailedFutureWhenGetServerErrorOnConsortiumDataLoading(VertxTestContext context) {
    WIRE_MOCK.stubFor(get(USER_TENANTS_PATH).willReturn(WireMock.serverError()));

    Future<Optional<ConsortiumData>> future = consortiumDataCache.getConsortiumData(TENANT_ID, okapiHeaders);

    future.onComplete(ar -> {
      context.verify(() -> assertTrue(ar.failed()));
      context.completeNow();
    });
  }

  @Test
  void shouldReturnFailedFutureWhenSpecifiedTenantIdIsNull(VertxTestContext context) {
    WIRE_MOCK.stubFor(get(USER_TENANTS_PATH).willReturn(WireMock.serverError()));

    Future<Optional<ConsortiumData>> future = consortiumDataCache.getConsortiumData(null, okapiHeaders);

    future.onComplete(ar -> {
      context.verify(() -> assertTrue(ar.failed()));
      context.completeNow();
    });
  }

  @Test
  void shouldFailWhenGetForbiddenErrorOnConsortiumDataLoading(VertxTestContext context) {
    WIRE_MOCK.stubFor(get(USER_TENANTS_PATH).willReturn(WireMock.forbidden()));

    Future<Optional<ConsortiumData>> future = consortiumDataCache.getConsortiumData(TENANT_ID, okapiHeaders);

    future.onComplete(ar -> {
      context.verify(() -> assertTrue(ar.failed()));
      context.completeNow();
    });
  }

  @Test
  void shouldUseCentralTenantHeaderWhenLoadingConsortiumTenants(VertxTestContext context) {
    var centralTenantId = "central";
    var consortiumId = UUID.randomUUID().toString();

    WIRE_MOCK.stubFor(get(USER_TENANTS_PATH).willReturn(WireMock.ok().withBody(new JsonObject()
      .put(USER_TENANTS_FIELD, new JsonArray().add(new JsonObject()
        .put(CENTRAL_TENANT_ID_FIELD, centralTenantId)
        .put(CONSORTIUM_ID_FIELD, consortiumId))).encodePrettily())));

    WIRE_MOCK.stubFor(get(urlMatching("/consortia/.*/tenants"))
      .withHeader(XOkapiHeaders.TENANT, equalTo(centralTenantId))
      .willReturn(WireMock.ok().withBody(new JsonObject()
        .put(ECS_TENANTS_FIELD, new JsonArray()
          .add(new JsonObject().put("id", centralTenantId).put("isCentral", true))
          .add(new JsonObject().put("id", "memberA").put("isCentral", false))
          .add(new JsonObject().put("id", "memberB").put("isCentral", false))).encodePrettily())));

    consortiumDataCache.getConsortiumData(TENANT_ID, okapiHeaders).onComplete(ar -> {
      context.verify(() -> {
        assertTrue(ar.succeeded(), "load should succeed when call uses central tenant header");
        ConsortiumData data = ar.result().orElseThrow();
        assertEquals(2, data.memberTenants().size(), "non-central member tenants must be returned");
      });
      context.completeNow();
    });
  }
}
