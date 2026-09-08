package org.folio.services.consortium;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.services.caches.ConsortiumData;
import org.folio.services.caches.ConsortiumDataCache;
import org.folio.services.consortium.entities.SharingInstance;
import org.folio.services.consortium.entities.SharingStatus;
import org.folio.services.consortium.exceptions.ConsortiumException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
class ConsortiumServiceImplTest {

  @RegisterExtension
  static WireMockExtension mockServer = WireMockExtension.newInstance()
    .options(WireMockConfiguration.wireMockConfig()
      .notifier(new ConsoleNotifier(false))
      .dynamicPort())
    .configureStaticDsl(true)
    .build();

  private static final String TENANT_ID = "diku";
  private static final String CENTRAL_TENANT_ID = "mobius";
  private static final String INSTANCE_ID = UUID.randomUUID().toString();
  private static final String CONSORTIUM_ID = "consortium_id";
  private static final String TOKEN = "token";

  private static final String SOURCE_TENANT_ID_FIELD = "sourceTenantId";
  private static final String TARGET_TENANT_ID_FIELD = "targetTenantId";
  private static final String INSTANCE_ID_FIELD = "instanceIdentifier";
  private static final String STATUS_FIELD = "status";

  private static final String INSTANCE_SHARE_PATH = String.format("/consortia/%s/sharing/instances",
    CONSORTIUM_ID);

  private ConsortiumServiceImpl consortiumServiceImpl;
  private Map<String, String> okapiHeaders;

  @Mock
  private ConsortiumDataCache consortiumDataCache;

  @BeforeEach
  void setUp(Vertx vertx) {
    consortiumServiceImpl = new ConsortiumServiceImpl(vertx.createHttpClient(), consortiumDataCache);
    okapiHeaders = Map.of(
      XOkapiHeaders.TENANT, TENANT_ID,
      XOkapiHeaders.TOKEN, TOKEN,
      XOkapiHeaders.URL, mockServer.baseUrl());
  }

  @AfterEach
  void reset() {
    WireMock.reset();
  }

  @Test
  void shouldCreateSharedInstance(VertxTestContext context) {
    ConsortiumData data = new ConsortiumData(CENTRAL_TENANT_ID, CONSORTIUM_ID, Collections.emptyList());

    JsonObject sharingInstance = new JsonObject()
      .put(SOURCE_TENANT_ID_FIELD, CENTRAL_TENANT_ID)
      .put(TARGET_TENANT_ID_FIELD, TENANT_ID)
      .put(INSTANCE_ID_FIELD, INSTANCE_ID)
      .put(STATUS_FIELD, "COMPLETE");

    WireMock.stubFor(post(INSTANCE_SHARE_PATH)
      .willReturn(WireMock.created().withBody(sharingInstance.encodePrettily())));

    Future<SharingInstance> future = consortiumServiceImpl.createShadowInstance(INSTANCE_ID, data, okapiHeaders);

    future.onComplete(ar -> {
      context.verify(() -> {
        verifyShareInstanceCall();
        assertTrue(ar.succeeded());
        SharingInstance instance = ar.result();
        assertNotNull(instance);
        assertEquals(UUID.fromString(INSTANCE_ID), instance.getInstanceIdentifier());
        assertEquals(TENANT_ID, instance.getTargetTenantId());
        assertEquals(CENTRAL_TENANT_ID, instance.getSourceTenantId());
        assertEquals(SharingStatus.valueOf("COMPLETE"), instance.getStatus());
      });
      context.completeNow();
    });
  }

  @Test
  void shouldReturnFailureForSharingErrorStatus(VertxTestContext context) {
    ConsortiumData data = new ConsortiumData(CENTRAL_TENANT_ID, CONSORTIUM_ID, Collections.emptyList());

    JsonObject sharingInstance = new JsonObject()
      .put(SOURCE_TENANT_ID_FIELD, CENTRAL_TENANT_ID)
      .put(TARGET_TENANT_ID_FIELD, TENANT_ID)
      .put(INSTANCE_ID_FIELD, INSTANCE_ID)
      .put(STATUS_FIELD, "ERROR");

    WireMock.stubFor(post(INSTANCE_SHARE_PATH)
      .willReturn(WireMock.created().withBody(sharingInstance.encodePrettily())));

    Future<SharingInstance> future = consortiumServiceImpl.createShadowInstance(INSTANCE_ID, data, okapiHeaders);

    future.onComplete(ar -> {
      context.verify(() -> {
        verifyShareInstanceCall();
        assertTrue(ar.failed());
        assertInstanceOf(ConsortiumException.class, ar.cause());
      });
      context.completeNow();
    });
  }

  @Test
  void shouldReturnFailureForErrorOnSharing(VertxTestContext context) {
    ConsortiumData data = new ConsortiumData(CENTRAL_TENANT_ID, CONSORTIUM_ID, Collections.emptyList());

    WireMock.stubFor(post(INSTANCE_SHARE_PATH).willReturn(WireMock.serverError()));

    Future<SharingInstance> future = consortiumServiceImpl.createShadowInstance(INSTANCE_ID, data, okapiHeaders);

    future.onComplete(ar -> {
      context.verify(() -> {
        verifyShareInstanceCall();
        assertTrue(ar.failed());
        assertInstanceOf(ConsortiumException.class, ar.cause());
      });
      context.completeNow();
    });
  }

  @Test
  void shouldShareInstance(VertxTestContext testContext) {
    SharingInstance sharingInstance = new SharingInstance();
    sharingInstance.setSourceTenantId(CENTRAL_TENANT_ID);
    sharingInstance.setInstanceIdentifier(UUID.fromString(INSTANCE_ID));
    sharingInstance.setTargetTenantId(TENANT_ID);

    JsonObject sharingInstanceResult = new JsonObject()
      .put(SOURCE_TENANT_ID_FIELD, CENTRAL_TENANT_ID)
      .put(TARGET_TENANT_ID_FIELD, TENANT_ID)
      .put(INSTANCE_ID_FIELD, INSTANCE_ID)
      .put(STATUS_FIELD, "COMPLETE");

    WireMock.stubFor(post(INSTANCE_SHARE_PATH)
      .willReturn(WireMock.created().withBody(sharingInstanceResult.encodePrettily())));

    consortiumServiceImpl.shareInstance(CONSORTIUM_ID, sharingInstance, okapiHeaders).onComplete(ar -> {
      testContext.verify(() -> {
        verify(postRequestedFor(urlMatching(INSTANCE_SHARE_PATH))
          .withHeader(XOkapiHeaders.TENANT, equalTo(TENANT_ID))
          .withHeader(XOkapiHeaders.TOKEN, equalTo(TOKEN))
          .withHeader(XOkapiHeaders.URL, WireMock.equalTo(mockServer.baseUrl())));
        assertTrue(ar.succeeded());
        assertEquals(SharingStatus.COMPLETE, ar.result().getStatus());
      });
      testContext.completeNow();
    });
  }

  @Test
  void shouldGetConsortiumData(VertxTestContext testContext) {
    ConsortiumData consortiumData = new ConsortiumData(CENTRAL_TENANT_ID, CONSORTIUM_ID, Collections.emptyList());
    when(consortiumDataCache.getConsortiumData(TENANT_ID, okapiHeaders))
      .thenReturn(Future.succeededFuture(Optional.of(consortiumData)));

    consortiumServiceImpl.getConsortiumData(okapiHeaders).onComplete(ar -> {
      testContext.verify(() -> {
        assertTrue(ar.succeeded());
        assertTrue(ar.result().isPresent());
        assertEquals(consortiumData, ar.result().get());
      });
      testContext.completeNow();
    });
  }

  private void verifyShareInstanceCall() {
    verify(postRequestedFor(urlMatching(INSTANCE_SHARE_PATH))
      .withHeader(XOkapiHeaders.TENANT, equalTo(CENTRAL_TENANT_ID))
      .withHeader(XOkapiHeaders.TOKEN, equalTo(TOKEN))
      .withHeader(XOkapiHeaders.URL, WireMock.equalTo(mockServer.baseUrl())));
  }
}
