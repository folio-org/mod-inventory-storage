package org.folio.services.consortium;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
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
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(VertxUnitRunner.class)
public class ConsortiumServiceImplTest {

  @ClassRule
  public static WireMockRule mockServer = new WireMockRule(WireMockConfiguration.wireMockConfig()
    .notifier(new ConsoleNotifier(false))
    .dynamicPort());

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

  private final Vertx vertx = Vertx.vertx();
  private ConsortiumServiceImpl consortiumServiceImpl;
  private Map<String, String> okapiHeaders;
  @Mock
  private ConsortiumDataCache consortiumDataCache;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    consortiumServiceImpl = new ConsortiumServiceImpl(vertx.createHttpClient(), consortiumDataCache);
    okapiHeaders = Map.of(
      XOkapiHeaders.TENANT, TENANT_ID,
      XOkapiHeaders.TOKEN, TOKEN,
      XOkapiHeaders.URL, mockServer.baseUrl());
  }

  @After
  public void reset() {
    WireMock.reset();
  }

  @Test
  public void shouldCreateSharedInstance(TestContext context) {
    Async async = context.async();
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
      verifyShareInstanceCall();
      context.assertTrue(ar.succeeded());
      SharingInstance instance = ar.result();
      context.assertNotNull(instance);
      context.assertEquals(UUID.fromString(INSTANCE_ID), instance.getInstanceIdentifier());
      context.assertEquals(TENANT_ID, instance.getTargetTenantId());
      context.assertEquals(CENTRAL_TENANT_ID, instance.getSourceTenantId());
      context.assertEquals(SharingStatus.valueOf("COMPLETE"), instance.getStatus());
      async.complete();
    });
  }

  @Test
  public void shouldReturnFailureForSharingErrorStatus(TestContext context) {
    Async async = context.async();
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
      verifyShareInstanceCall();
      context.assertTrue(ar.failed());
      context.assertTrue(ar.cause() instanceof ConsortiumException);
      async.complete();
    });
  }

  @Test
  public void shouldReturnFailureForErrorOnSharing(TestContext context) {
    Async async = context.async();
    ConsortiumData data = new ConsortiumData(CENTRAL_TENANT_ID, CONSORTIUM_ID, Collections.emptyList());

    WireMock.stubFor(post(INSTANCE_SHARE_PATH).willReturn(WireMock.serverError()));

    Future<SharingInstance> future = consortiumServiceImpl.createShadowInstance(INSTANCE_ID, data, okapiHeaders);

    future.onComplete(ar -> {
      verifyShareInstanceCall();
      context.assertTrue(ar.failed());
      context.assertTrue(ar.cause() instanceof ConsortiumException);
      async.complete();
    });
  }

  @Test
  public void shouldShareInstance(TestContext testContext) {
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

    Async async = testContext.async();

    consortiumServiceImpl.shareInstance(CONSORTIUM_ID, sharingInstance, okapiHeaders).onComplete(ar -> {
      verify(postRequestedFor(urlMatching(INSTANCE_SHARE_PATH))
        .withHeader(XOkapiHeaders.TENANT, equalTo(TENANT_ID))
        .withHeader(XOkapiHeaders.TOKEN, equalTo(TOKEN))
        .withHeader(XOkapiHeaders.URL, WireMock.equalTo(mockServer.baseUrl())));
      testContext.assertTrue(ar.succeeded());
      testContext.assertEquals(ar.result().getStatus(), SharingStatus.COMPLETE);
      async.complete();
    });
  }

  @Test
  public void shouldGetConsortiumData(TestContext testContext) {
    Async async = testContext.async();

    ConsortiumData consortiumData = new ConsortiumData(CENTRAL_TENANT_ID, CONSORTIUM_ID, Collections.emptyList());
    when(consortiumDataCache.getConsortiumData(TENANT_ID, okapiHeaders))
      .thenReturn(Future.succeededFuture(Optional.of(consortiumData)));

    consortiumServiceImpl.getConsortiumData(okapiHeaders).onComplete(ar -> {
      testContext.assertTrue(ar.succeeded());
      testContext.assertTrue(ar.result().isPresent());
      testContext.assertEquals(consortiumData, ar.result().get());
      async.complete();
    });
  }

  private void verifyShareInstanceCall() {
    verify(postRequestedFor(urlMatching(INSTANCE_SHARE_PATH))
      .withHeader(XOkapiHeaders.TENANT, equalTo(CENTRAL_TENANT_ID))
      .withHeader(XOkapiHeaders.TOKEN, equalTo(TOKEN))
      .withHeader(XOkapiHeaders.URL, WireMock.equalTo(mockServer.baseUrl())));
  }
}
