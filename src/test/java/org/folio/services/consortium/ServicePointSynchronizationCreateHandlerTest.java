package org.folio.services.consortium;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.folio.okapi.common.XOkapiHeaders.TENANT;
import static org.folio.okapi.common.XOkapiHeaders.TOKEN;
import static org.folio.okapi.common.XOkapiHeaders.URL;
import static org.folio.rest.api.ServicePointTest.createHoldShelfExpiryPeriod;
import static org.folio.rest.support.http.InterfaceUrls.servicePointsUrl;
import static org.folio.utility.LocationUtility.createServicePoint;
import static org.folio.utility.ModuleUtility.getClient;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mockStatic;

import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.kafka.client.consumer.impl.KafkaConsumerRecordImpl;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import lombok.SneakyThrows;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.folio.InventoryKafkaTopic;
import org.folio.rest.api.TestBase;
import org.folio.rest.jaxrs.model.Servicepoint;
import org.folio.services.caches.ConsortiumData;
import org.folio.services.caches.ConsortiumDataCache;
import org.folio.services.consortium.handler.ServicePointSynchronizationCreateHandler;
import org.folio.services.domainevent.DomainEvent;
import org.folio.utility.ModuleUtility;
import org.folio.utils.Environment;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@RunWith(VertxUnitRunner.class)
public class ServicePointSynchronizationCreateHandlerTest extends TestBase {

  @ClassRule
  public static WireMockRule mockServer = new WireMockRule(
    WireMockConfiguration.wireMockConfig()
      .notifier(new ConsoleNotifier(false))
      .dynamicPort());

  private static final String CENTRAL_TENANT_ID = "consortium";
  private static final String CONSORTIUM_ID = UUID.randomUUID().toString();
  private static final String COLLEGE_TENANT_ID = "college";
  private static final UUID SERVICE_POINT_ID = UUID.randomUUID();
  private static final String ECS_TLR_FEATURE_ENABLED = "ECS_TLR_FEATURE_ENABLED";

  @Mock
  private ConsortiumDataCache consortiumDataCache;
  private Vertx vertx = Vertx.vertx();
  private ServicePointSynchronizationCreateHandler synchronizationHandler;

  @BeforeClass
  public static void setUpClass()
    throws ExecutionException, InterruptedException, TimeoutException {
    ModuleUtility.prepareTenant(CENTRAL_TENANT_ID, false);
    ModuleUtility.prepareTenant(COLLEGE_TENANT_ID, false);

    // mock env variable
    MockedStatic<Environment> mockedStatic = mockStatic(Environment.class);
    mockedStatic.when(() -> Environment.getEnvVar(ECS_TLR_FEATURE_ENABLED, FALSE.toString()))
      .thenReturn(TRUE.toString());
  }

  @Before
  @SneakyThrows(Exception.class)
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    clearData();

    synchronizationHandler = new ServicePointSynchronizationCreateHandler(consortiumDataCache,
      vertx.createHttpClient(), vertx);
    Mockito.when(consortiumDataCache.getConsortiumData(anyMap()))
      .thenReturn(
        Future.succeededFuture(Optional.of(new ConsortiumData(CENTRAL_TENANT_ID, CONSORTIUM_ID,
          List.of(COLLEGE_TENANT_ID)))));
  }

  @Test
  public void shouldDistributeCreationOfServicePointOnLendingTenant(TestContext context) {
    var servicepoint = createServicePointAgainstTenant(CENTRAL_TENANT_ID, false);
    var servicepointDomainEvent = DomainEvent.createEvent(servicepoint,
      CENTRAL_TENANT_ID);
    var kafkaRecord = buildKafkaRecord(servicepoint.getId(),
      servicepointDomainEvent);

    synchronizationHandler.handle(kafkaRecord)
      .compose(value -> getServicePointById(servicepoint.getId(), COLLEGE_TENANT_ID))
      .onComplete(context.asyncAssertSuccess(
        createdLendingSp -> context.assertEquals(
          servicepoint.getDiscoveryDisplayName(),
          createdLendingSp.getDiscoveryDisplayName())));
  }

  @SneakyThrows(Exception.class)
  private static Servicepoint createServicePointAgainstTenant(String tenantId, boolean updated) {
    String discoveryDisplayName = "Circulation Desk -- Basement" + (updated ? "(updated)" : "");
    return createServicePoint(SERVICE_POINT_ID, "Circ Desk 2522", "cd2522",
      discoveryDisplayName, null, 20,
      true, createHoldShelfExpiryPeriod(), tenantId)
      .getJson().mapTo(Servicepoint.class);
  }

  private Future<Servicepoint> getServicePointById(String servicePointId, String tenantId) {
    Promise<HttpResponse<Buffer>> promise = Promise.promise();
    getClient().get(servicePointsUrl("/" + servicePointId), tenantId, promise::complete);

    return promise.future().map(resp -> {
      MatcherAssert.assertThat(resp.statusCode(), CoreMatchers.is(HTTP_OK));
      return resp.bodyAsJson(Servicepoint.class);
    });
  }

  private static KafkaConsumerRecordImpl<String, String> buildKafkaRecord(String recordKey,
    DomainEvent<Servicepoint> event) {

    String topic = InventoryKafkaTopic.SERVICE_POINT.fullTopicName(CENTRAL_TENANT_ID);
    ConsumerRecord<String, String> consumerRecord = new ConsumerRecord<>(topic, 0, 0, recordKey,
      Json.encode(event));
    consumerRecord.headers()
      .add(new RecordHeader(TENANT.toLowerCase(), CENTRAL_TENANT_ID.getBytes()));
    consumerRecord.headers()
      .add(new RecordHeader(URL.toLowerCase(), mockServer.baseUrl().getBytes()));
    consumerRecord.headers().add(new RecordHeader(TOKEN.toLowerCase(), "test-token".getBytes()));
    return new KafkaConsumerRecordImpl<>(consumerRecord);
  }

}
