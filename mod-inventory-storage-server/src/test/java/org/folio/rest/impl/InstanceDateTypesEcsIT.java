package org.folio.rest.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.folio.HttpStatus.HTTP_NO_CONTENT;
import static org.folio.services.instance.InstanceDateTypeService.INSTANCE_DATE_TYPE_TABLE;
import static org.folio.utility.RestUtility.CONSORTIUM_CENTRAL_TENANT;
import static org.folio.utility.RestUtility.CONSORTIUM_ID;
import static org.folio.utility.RestUtility.CONSORTIUM_MEMBER_TENANT;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.folio.rest.jaxrs.model.DisplayFormat;
import org.folio.rest.jaxrs.model.InstanceDateType;
import org.folio.rest.jaxrs.model.InstanceDateTypePatch;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.extension.EnableTenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@EnableTenant(tenants = {CONSORTIUM_MEMBER_TENANT, CONSORTIUM_CENTRAL_TENANT})
class InstanceDateTypesEcsIT extends BaseIntegrationTest {

  private static final String USER_TENANTS_PATH = "/user-tenants?limit=1";
  private static final String CONSORTIUM_TENANTS_PATH = "/consortia/%s/tenants".formatted(CONSORTIUM_ID);

  @BeforeEach
  void beforeEach() {
    mockUserTenantsForConsortiumMember();
    mockConsortiumTenants();
  }

  @Test
  void patch_shouldReturn204AndRecordIsUpdated(Vertx vertx, VertxTestContext ctx) {
    var centralRecordUpdated = ctx.checkpoint();
    var memberRecordUpdated = ctx.checkpoint();
    HttpClient client = vertx.createHttpClient();

    var postgresCenterClient = PostgresClient.getInstance(vertx, CONSORTIUM_CENTRAL_TENANT);
    var postgresMemberClient = PostgresClient.getInstance(vertx, CONSORTIUM_MEMBER_TENANT);

    var newRecord = createInstanceDateType();

    postgresMemberClient.save(INSTANCE_DATE_TYPE_TABLE, newRecord.getId(), newRecord)
      .compose(s -> postgresCenterClient.save(INSTANCE_DATE_TYPE_TABLE, newRecord.getId(), newRecord))
      .compose(id -> patchAndVerifyCentralRecord(client, postgresCenterClient, id, ctx, centralRecordUpdated));

    verifyMemberRecordUpdated(vertx, postgresMemberClient, newRecord.getId(), memberRecordUpdated);
  }

  private InstanceDateType createInstanceDateType() {
    return new InstanceDateType()
      .withId(UUID.randomUUID().toString())
      .withName("name")
      .withCode("c")
      .withDisplayFormat(new DisplayFormat().withDelimiter(",").withKeepDelimiter(false))
      .withSource(InstanceDateType.Source.FOLIO);
  }

  private Future<?> patchAndVerifyCentralRecord(HttpClient client,
                                                                PostgresClient postgresCenterClient,
                                                                String id, VertxTestContext ctx,
                                                                io.vertx.junit5.Checkpoint checkpoint) {
    var updatedRecord = new InstanceDateTypePatch().withName("Updated");
    return doPatch(client, "/instance-date-types/" + id, CONSORTIUM_CENTRAL_TENANT, pojo2JsonObject(updatedRecord))
      .onComplete(verifyStatus(ctx, HTTP_NO_CONTENT))
      .compose(r -> postgresCenterClient.getById(INSTANCE_DATE_TYPE_TABLE, id, InstanceDateType.class)
        .onComplete(ctx.succeeding(dbRecord -> ctx.verify(() -> {
          assertThat(dbRecord)
            .extracting(InstanceDateType::getName)
            .isEqualTo(updatedRecord.getName());
          checkpoint.flag();
        }))));
  }

  private void verifyMemberRecordUpdated(Vertx vertx, PostgresClient postgresMemberClient,
                                          String recordId, io.vertx.junit5.Checkpoint checkpoint) {
    AtomicReference<InstanceDateType> atomicReference = new AtomicReference<>();

    vertx.setPeriodic(1000, 1000, event -> postgresMemberClient
      .getById(INSTANCE_DATE_TYPE_TABLE, recordId, InstanceDateType.class)
      .onComplete(e -> {
        if (e.succeeded()) {
          atomicReference.set(e.result());
        }
      }));

    await().atMost(1, TimeUnit.MINUTES).untilAsserted(() -> {
      assertThat(atomicReference.get()).isNotNull()
        .extracting(InstanceDateType::getName)
        .isEqualTo("Updated");
      checkpoint.flag();
    });
  }

  private static void mockUserTenantsForConsortiumMember() {
    JsonObject userTenantsCollection = new JsonObject()
      .put("userTenants", new JsonArray()
        .add(new JsonObject()
          .put("centralTenantId", CONSORTIUM_CENTRAL_TENANT)
          .put("consortiumId", CONSORTIUM_ID)));
    wm.stubFor(WireMock.get(USER_TENANTS_PATH)
      .willReturn(WireMock.ok().withBody(userTenantsCollection.encodePrettily())));
  }

  private static void mockConsortiumTenants() {
    JsonObject tenantsCollection = new JsonObject()
      .put("tenants", new JsonArray()
        .add(new JsonObject()
          .put("id", CONSORTIUM_CENTRAL_TENANT)
          .put("isCentral", true))
        .add(new JsonObject()
          .put("id", CONSORTIUM_MEMBER_TENANT)
          .put("isCentral", false)));
    wm.stubFor(WireMock.get(CONSORTIUM_TENANTS_PATH)
      .willReturn(WireMock.ok().withBody(tenantsCollection.encodePrettily())));
  }
}
