package org.folio.services.consortium;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static java.lang.String.format;
import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.util.Collections.emptyList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.okapi.common.XOkapiHeaders.TENANT;
import static org.folio.okapi.common.XOkapiHeaders.TOKEN;
import static org.folio.okapi.common.XOkapiHeaders.URL;
import static org.folio.rest.support.ResponseHandler.json;
import static org.folio.rest.support.http.InterfaceUrls.instanceTypesStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.instancesStorageUrl;
import static org.folio.utility.ModuleUtility.getClient;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.matching.RegexPattern;
import com.github.tomakehurst.wiremock.matching.UrlPathPattern;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.kafka.client.consumer.impl.KafkaConsumerRecordImpl;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.folio.InventoryKafkaTopic;
import org.folio.rest.api.TestBase;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.InstanceType;
import org.folio.rest.support.Response;
import org.folio.services.caches.ConsortiumData;
import org.folio.services.caches.ConsortiumDataCache;
import org.folio.services.domainevent.DomainEvent;
import org.folio.services.domainevent.DomainEventType;
import org.folio.utility.ModuleUtility;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@RunWith(VertxUnitRunner.class)
public class ShadowInstanceSynchronizationHandlerTest extends TestBase {

  @ClassRule
  public static WireMockRule mockServer = new WireMockRule(WireMockConfiguration.wireMockConfig()
    .notifier(new ConsoleNotifier(false))
    .dynamicPort());

  private static final String SHARING_JOBS_PATH = "/consortia/.{36}/sharing/instances";
  private static final String CENTRAL_TENANT_ID = "mobius";
  private static final String CONSORTIUM_ID = UUID.randomUUID().toString();
  private static final String SHARING_INSTANCES_FIELD = "sharingInstances";
  private static final String SOURCE_TENANT_ID_FIELD = "sourceTenantId";
  private static final String TARGET_TENANT_ID_FIELD = "targetTenantId";
  private static final String INSTANCE_IDENTIFIER_FIELD = "instanceIdentifier";
  private static final String INSTANCE_TYPE_ID = "bbe13900-61c6-4643-8d73-2e60d38c8e55";

  private static final InstanceType INSTANCE_TYPE = new InstanceType()
    .withId(INSTANCE_TYPE_ID)
    .withCode("DIT")
    .withName("Default Instance Type")
    .withSource("local");

  private final Vertx vertx = Vertx.vertx();

  @Mock
  private ConsortiumDataCache consortiaDataCache;
  private ShadowInstanceSynchronizationHandler synchronizationHandler;

  @BeforeClass
  public static void setUpClass() throws ExecutionException, InterruptedException, TimeoutException {
    ModuleUtility.prepareTenant(CENTRAL_TENANT_ID, false);
    createInstanceType(INSTANCE_TYPE, CENTRAL_TENANT_ID);
    createInstanceType(INSTANCE_TYPE, TENANT_ID);
  }

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    clearData();
    synchronizationHandler =
      new ShadowInstanceSynchronizationHandler(consortiaDataCache, vertx.createHttpClient(), vertx);

    JsonObject sharingCollection = new JsonObject()
      .put(SHARING_INSTANCES_FIELD, JsonArray.of(new JsonObject()
        .put(SOURCE_TENANT_ID_FIELD, CENTRAL_TENANT_ID)
        .put(TARGET_TENANT_ID_FIELD, TENANT_ID)
        .put(INSTANCE_IDENTIFIER_FIELD, UUID.randomUUID().toString())));

    WireMock.stubFor(WireMock.get(new UrlPathPattern(new RegexPattern(SHARING_JOBS_PATH), true))
      .willReturn(WireMock.ok().withBody(sharingCollection.encodePrettily())));
    Mockito.when(consortiaDataCache.getConsortiumData(anyString(), anyMap()))
      .thenReturn(Future.succeededFuture(Optional.of(new ConsortiumData(CENTRAL_TENANT_ID, CONSORTIUM_ID,
        emptyList()))));
  }

  @Test
  public void shouldUpdateShadowInstance(TestContext context)
    throws ExecutionException, InterruptedException, TimeoutException {
    Instance shadowInstance = new Instance()
      .withId(UUID.randomUUID().toString())
      .withInstanceTypeId(INSTANCE_TYPE_ID)
      .withTitle("test-title")
      .withSource("CONSORTIUM-MARC");

    Instance sharedInstance = new Instance()
      .withId(shadowInstance.getId())
      .withInstanceTypeId(INSTANCE_TYPE_ID)
      .withTitle("test-title-updated")
      .withSource("MARC");

    createInstance(sharedInstance, CENTRAL_TENANT_ID);
    createInstance(shadowInstance, TENANT_ID);

    DomainEvent<Instance> event = DomainEvent.updateEvent(sharedInstance, sharedInstance, CENTRAL_TENANT_ID);
    KafkaConsumerRecordImpl<String, String> kafkaRecord = buildKafkaRecord(sharedInstance.getId(), event);

    synchronizationHandler.handle(kafkaRecord)
      .compose(v -> getInstanceById(sharedInstance.getId(), TENANT_ID))
      .onComplete(context.asyncAssertSuccess(
        updatedShadowInstance -> context.assertEquals(sharedInstance.getTitle(), updatedShadowInstance.getTitle())));
  }

  @Test
  public void shouldNotUpdateShadowInstanceIfEventTypeIsNotUpdate(TestContext context) {
    Instance instance = new Instance()
      .withId(UUID.randomUUID().toString())
      .withInstanceTypeId(INSTANCE_TYPE_ID)
      .withTitle("title")
      .withSource("MARC");

    DomainEvent<Instance> event = DomainEvent.createEvent(instance, CENTRAL_TENANT_ID);
    context.assertNotEquals(DomainEventType.UPDATE, event.getType());
    KafkaConsumerRecordImpl<String, String> kafkaRecord = buildKafkaRecord(instance.getId(), event);

    synchronizationHandler.handle(kafkaRecord)
      .onComplete(context.asyncAssertSuccess(v -> verify(0, getRequestedFor(urlMatching(SHARING_JOBS_PATH)))));
  }

  @Test
  public void shouldNotInitiateShadowInstanceUpdateIfEventContainsNonCentralTenant(TestContext context) {
    Instance instance = new Instance().withId(UUID.randomUUID().toString());
    DomainEvent<Instance> event = DomainEvent.updateEvent(instance, instance, TENANT_ID);
    context.assertNotEquals(CENTRAL_TENANT_ID, event.getTenant());
    KafkaConsumerRecordImpl<String, String> kafkaRecord = buildKafkaRecord(instance.getId(), event);

    synchronizationHandler.handle(kafkaRecord)
      .onComplete(context.asyncAssertSuccess(v -> verify(0, getRequestedFor(urlMatching(SHARING_JOBS_PATH)))));
  }

  @Test
  public void shouldReturnFailedFutureIfFailedToGetInstanceSharingActions(TestContext context) {
    WireMock.stubFor(WireMock.get(new UrlPathPattern(new RegexPattern(SHARING_JOBS_PATH), true))
      .willReturn(WireMock.serverError()));

    Instance instance = new Instance().withId(UUID.randomUUID().toString());
    DomainEvent<Instance> event = DomainEvent.updateEvent(instance, instance, CENTRAL_TENANT_ID);
    KafkaConsumerRecordImpl<String, String> kafkaRecord = buildKafkaRecord(instance.getId(), event);

    synchronizationHandler.handle(kafkaRecord)
      .onComplete(context.asyncAssertFailure(v -> verify(1, getRequestedFor(urlMatching(SHARING_JOBS_PATH + ".+")))));
  }

  private static void createInstanceType(InstanceType instanceType, String tenantId)
    throws InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();
    getClient().post(instanceTypesStorageUrl(""), instanceType, tenantId, json(createCompleted));
    Response response = createCompleted.get(2, SECONDS);

    assertThat(format("Create instance type failed: %s", response.getBody()),
      response.getStatusCode(), is(HTTP_CREATED));
  }

  private void createInstance(Instance instanceToCreate, String tenantId)
    throws InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();
    getClient().post(instancesStorageUrl(""), instanceToCreate, tenantId, json(createCompleted));
    Response response = createCompleted.get(2, SECONDS);

    assertThat(format("Create instance failed: %s", response.getBody()),
      response.getStatusCode(), is(HTTP_CREATED));
  }

  private Future<Instance> getInstanceById(String instanceId, String tenantId) {
    Promise<HttpResponse<Buffer>> promise = Promise.promise();
    getClient().get(instancesStorageUrl("/" + instanceId), tenantId, promise::complete);

    return promise.future().map(resp -> {
      assertThat(format("Create instance failed: %s", resp.bodyAsString()), resp.statusCode(), is(HTTP_OK));
      return resp.bodyAsJson(Instance.class);
    });
  }

  private static KafkaConsumerRecordImpl<String, String> buildKafkaRecord(String recordKey,
                                                                          DomainEvent<Instance> event) {
    String topic = InventoryKafkaTopic.INSTANCE.fullTopicName(CENTRAL_TENANT_ID);
    ConsumerRecord<String, String> consumerRecord = new ConsumerRecord<>(topic, 0, 0, recordKey, Json.encode(event));
    consumerRecord.headers().add(new RecordHeader(TENANT.toLowerCase(), CENTRAL_TENANT_ID.getBytes()));
    consumerRecord.headers().add(new RecordHeader(URL.toLowerCase(), mockServer.baseUrl().getBytes()));
    consumerRecord.headers().add(new RecordHeader(TOKEN.toLowerCase(), "test-token".getBytes()));
    return new KafkaConsumerRecordImpl<>(consumerRecord);
  }
}
