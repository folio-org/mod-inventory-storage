package org.folio.it.api;

import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.apache.http.HttpStatus.SC_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;
import static org.folio.services.instance.InstanceDateTypeService.INSTANCE_DATE_TYPE_TABLE;
import static org.folio.support.ResourcePaths.INSTANCE_DATE_TYPES;
import static org.folio.utility.RestUtility.CONSORTIUM_CENTRAL_TENANT;
import static org.folio.utility.RestUtility.CONSORTIUM_MEMBER_TENANT;
import static org.folio.utility.RestUtility.TENANT_ID;

import io.vertx.core.Vertx;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.folio.it.BaseIntegrationTest;
import org.folio.rest.jaxrs.model.InstanceDateType;
import org.folio.rest.jaxrs.model.InstanceDateTypePatchRequest;
import org.folio.rest.jaxrs.model.InstanceDateTypes;
import org.folio.rest.persist.PostgresClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code instance_date_type} is one of {@link BaseIntegrationTest}'s
 * {@code MIGRATION_SEEDED_TABLES} - seeded once by a Liquibase migration on every tenant's
 * schema, and never re-seeded or truncated between classes. Rather than creating throwaway
 * records (which would need a unique single-character {@code code} - the schema caps it at
 * {@code maxLength: 1} - to avoid colliding with a seeded row or another test in this class),
 * these tests exercise real seeded rows directly and restore whatever they mutate in
 * {@link #restoreSeededNames}, so nothing leaks into whichever {@code *IT} class runs next in the
 * shared JVM.
 */
class InstanceDateTypesIT extends BaseIntegrationTest {

  // "Single known date/probable date" (code "s") - read-only in this class, never mutated.
  private static final String QUERY_TEST_ID = "24a506e8-2a92-4ecc-bd09-ff849321fd5a";
  private static final String QUERY_TEST_CODE = "s";
  private static final String QUERY_TEST_NAME = "Single known date/probable date";
  // "No dates given; B.C. date involved" (code "b") - patched and restored on the default tenant.
  private static final String PATCH_TEST_ID = "77a09c3c-37bd-4ad3-aae4-9d86fc1b33d8";
  private static final String PATCH_TEST_ORIGINAL_NAME = "No dates given; B.C. date involved";
  // "Detailed date" (code "e") - patched on the central tenant, propagated to and restored on both.
  private static final String PROPAGATION_TEST_ID = "9669a463-5971-42dc-9eee-046bbd678fb1";
  private static final String PROPAGATION_TEST_ORIGINAL_NAME = "Detailed date";

  @AfterEach
  void restoreSeededNames(Vertx vertx) {
    restoreName(vertx, TENANT_ID, PATCH_TEST_ID, PATCH_TEST_ORIGINAL_NAME);
    restoreName(vertx, CONSORTIUM_CENTRAL_TENANT, PROPAGATION_TEST_ID, PROPAGATION_TEST_ORIGINAL_NAME);
    restoreName(vertx, CONSORTIUM_MEMBER_TENANT, PROPAGATION_TEST_ID, PROPAGATION_TEST_ORIGINAL_NAME);
  }

  @DisplayName("should return the matching instance date type when querying by code")
  @Test
  void shouldReturnMatchingRecord_whenQueryingByCode() {
    var response = await(doGet(client, INSTANCE_DATE_TYPES + "?query=code==" + QUERY_TEST_CODE + "&limit=500"));
    assertThat(response.status()).isEqualTo(SC_OK);

    var collection = response.jsonBody().mapTo(InstanceDateTypes.class);
    assertThat(collection.getTotalRecords()).isEqualTo(1);
    var found = collection.getInstanceDateTypes().getFirst();
    assertThat(found.getId()).isEqualTo(QUERY_TEST_ID);
    assertThat(found.getName()).isEqualTo(QUERY_TEST_NAME);
    assertThat(found.getCode()).isEqualTo(QUERY_TEST_CODE);
  }

  @DisplayName("should update an instance date type when patching it on the default tenant")
  @Test
  void shouldUpdateRecord_whenPatchingOnDefaultTenant(Vertx vertx) {
    var updatedRecord = new InstanceDateTypePatchRequest().withName("Updated");
    var response = await(doPatch(client, INSTANCE_DATE_TYPES + "/" + PATCH_TEST_ID, pojo2JsonObject(updatedRecord)));
    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);

    var postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);
    var dbRecord = await(
      postgresClient.getById(INSTANCE_DATE_TYPE_TABLE, PATCH_TEST_ID, InstanceDateType.class));
    assertThat(dbRecord.getName()).isEqualTo(updatedRecord.getName());
  }

  @DisplayName("should propagate an instance date type update to the member tenant "
               + "when patching it on the central tenant")
  @Test
  void shouldPropagateUpdate_whenPatchingOnCentralTenant(Vertx vertx) {
    var updatedRecord = new InstanceDateTypePatchRequest().withName("Updated");
    var response = await(doPatch(client, INSTANCE_DATE_TYPES + "/" + PROPAGATION_TEST_ID,
      CONSORTIUM_CENTRAL_TENANT, pojo2JsonObject(updatedRecord)));
    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);

    var centralRecord = await(PostgresClient.getInstance(vertx, CONSORTIUM_CENTRAL_TENANT)
      .getById(INSTANCE_DATE_TYPE_TABLE, PROPAGATION_TEST_ID, InstanceDateType.class));
    assertThat(centralRecord.getName()).isEqualTo(updatedRecord.getName());

    var postgresMemberClient = PostgresClient.getInstance(vertx, CONSORTIUM_MEMBER_TENANT);
    Awaitility.await().atMost(1, TimeUnit.MINUTES).untilAsserted(() -> {
      var memberRecord =
        await(postgresMemberClient.getById(INSTANCE_DATE_TYPE_TABLE, PROPAGATION_TEST_ID, InstanceDateType.class));
      assertThat(memberRecord).isNotNull();
      assertThat(memberRecord.getName()).isEqualTo("Updated");
    });
  }

  private static void restoreName(Vertx vertx, String tenantId, String id, String originalName) {
    var postgresClient = PostgresClient.getInstance(vertx, tenantId);
    var current = await(postgresClient.getById(INSTANCE_DATE_TYPE_TABLE, id, InstanceDateType.class));
    if (!originalName.equals(current.getName())) {
      await(postgresClient.update(INSTANCE_DATE_TYPE_TABLE, current.withName(originalName), id));
    }
  }
}
