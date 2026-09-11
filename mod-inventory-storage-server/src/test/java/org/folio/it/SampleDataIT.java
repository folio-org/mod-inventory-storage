package org.folio.it;

import static org.apache.http.HttpStatus.SC_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;

import io.vertx.core.json.JsonObject;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.folio.dataimport.testsupport.tenant.TenantTestSupport;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.support.ResourcePaths;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Verifies that sample data actually loads during tenant install. Like {@link ReferenceTablesIT}
 * (which this class mirrors), this is orthogonal to every other {@code *IT} class: the shared
 * {@code TENANT_ID} tenant installs with {@code loadSample=false} specifically to skip this (for
 * speed/isolation), so this class installs its own, separate tenant with
 * {@code loadReference=true, loadSample=true} on the same shared verticle instead of touching the
 * default one.
 */
class SampleDataIT extends BaseIntegrationTest {

  private static final String SAMPLE_DATA_TENANT = "sampledata";
  private static final String TOTAL_RECORDS_KEY = "totalRecords";
  private static final String METADATA_KEY = "metadata";
  private static final String CREATED_DATE_KEY = "createdDate";
  private static final String CREATED_BY_USER_ID_KEY = "createdByUserId";
  private static final String UPDATED_DATE_KEY = "updatedDate";
  private static final String UPDATED_BY_USER_ID_KEY = "updatedByUserId";

  @BeforeAll
  static void installTenantWithSampleData() {
    // Sample-data loading (holdings/items) triggers a consortium-membership check against
    // /user-tenants during tenant install itself, before this class's own @BeforeEach runs -
    // register the stub first or every sample holding/item POST fails with a 500.
    mockUserTenantsForNonConsortiumMember(SAMPLE_DATA_TENANT);
    installTenant(SAMPLE_DATA_TENANT, new TenantAttributes()
      .withModuleTo(MODULE_ID)
      .withParameters(TenantTestSupport.dataLoadingParameters(true, true)));
  }

  @BeforeEach
  void mockUserTenants() {
    mockUserTenantsForNonConsortiumMember(SAMPLE_DATA_TENANT);
  }

  @ParameterizedTest(name = "should load exactly {2} sample {0}")
  @MethodSource("sampleDataCounts")
  void shouldLoadSampleData(String description, String path, int exactCount) {
    var response = await(doGet(client, path + queryAll(), SAMPLE_DATA_TENANT));

    assertThat(response.status()).isEqualTo(SC_OK);
    assertThat(response.jsonBody().getInteger(TOTAL_RECORDS_KEY)).as(description).isEqualTo(exactCount);
  }

  private static Stream<Arguments> sampleDataCounts() {
    return Stream.of(
      Arguments.of("instances", ResourcePaths.INSTANCES, 36),
      Arguments.of("holdings records", ResourcePaths.HOLDINGS, 20),
      Arguments.of("items", ResourcePaths.ITEMS, 25),
      Arguments.of("instance relationships", ResourcePaths.INSTANCE_RELATIONSHIPS, 5),
      Arguments.of("bound-with parts", ResourcePaths.BOUND_WITH_PARTS, 10));
  }

  @DisplayName("should load the sample holdings record for Transparent water with its expected fields")
  @Test
  void shouldLoadHoldingsRecord_transparentWater() {
    var holding = getById(ResourcePaths.HOLDINGS, "e9285a1c-1dfc-4380-868c-e74073003f43");

    assertThat(holding.getString("instanceId")).isNotNull();
    assertThat(holding.getString("callNumber")).isEqualTo("M1366.S67 T73 2017");
    assertThat(holding.getString("permanentLocationId")).isEqualTo("fcd64ce1-6995-48f0-840e-89ffa2288371");
    assertMetadata(holding);
  }

  @DisplayName("should load the sample instance for Transparent water via its holdings record")
  @Test
  void shouldLoadInstance_transparentWater() {
    var holding = getById(ResourcePaths.HOLDINGS, "e9285a1c-1dfc-4380-868c-e74073003f43");
    var instance = getById(ResourcePaths.INSTANCES, holding.getString("instanceId"));

    assertThat(instance.getString("title")).isEqualTo("Transparent water");
    assertMetadata(instance);
  }

  @DisplayName("should load the sample item for Semantic Web Primer with its expected fields")
  @Test
  void shouldLoadItem_semanticWebPrimer() {
    var item = getById(ResourcePaths.ITEMS, "7212ba6a-8dcf-45a1-be9a-ffaa847c4423");

    assertThat(item.getJsonObject("status").getString("name")).isEqualTo("Available");
    assertThat(item.getString("holdingsRecordId")).isEqualTo("e3ff6133-b9a2-4d4c-a1c9-dc1867d4df19");
    assertThat(item.getString("barcode")).isEqualTo("10101");
    assertMetadata(item);
  }

  @DisplayName("should load the sample instance relationship linking the two Global Africa instances")
  @Test
  void shouldLoadInstanceRelationship_globalAfrica() {
    var relationship = getInstanceRelationshipById("e5cea7b1-3c48-428c-bc5e-2efc9ead1924");

    assertThat(relationship).as("Instance relationship could not be found").isNotNull();
    assertThat(relationship.getString("superInstanceId")).isNotNull();
    assertThat(relationship.getString("subInstanceId")).isNotNull();
    assertThat(relationship.getString("instanceRelationshipTypeId")).isEqualTo("30773a27-b485-4dab-aeb6-b8c04fa3cb17");
    assertMetadata(relationship);

    var superInstance = getById(ResourcePaths.INSTANCES, relationship.getString("superInstanceId"));
    assertThat(superInstance.getString("title")).isEqualTo("Global Africa");

    var subInstance = getById(ResourcePaths.INSTANCES, relationship.getString("subInstanceId"));
    assertThat(subInstance.getString("title")).isEqualTo(
      "Environment and identity politics in colonial Africa Fulani migrations and land conflict by Emmanuel M. Mbah");
  }

  private JsonObject getById(String path, String id) {
    var response = await(doGet(client, path + "/" + id, SAMPLE_DATA_TENANT));

    assertThat(response.status()).isEqualTo(SC_OK);
    return response.jsonBody();
  }

  private JsonObject getInstanceRelationshipById(String id) {
    var response =
      await(doGet(client, ResourcePaths.INSTANCE_RELATIONSHIPS + queryAll(), SAMPLE_DATA_TENANT));

    assertThat(response.status()).isEqualTo(SC_OK);
    return response.jsonBody().getJsonArray("instanceRelationships").stream()
      .map(JsonObject.class::cast)
      .filter(relationship -> id.equals(relationship.getString("id")))
      .findFirst()
      .orElse(null);
  }

  /**
   * Asserts that a sample-data entity has a metadata property where createdDate and updatedDate
   * are not null, and createdByUserId and updatedByUserId are null (sample data is loaded by the
   * module itself, not attributed to any user).
   */
  private void assertMetadata(JsonObject entity) {
    var metadata = entity.getJsonObject(METADATA_KEY);

    assertThat(metadata).isNotNull();
    assertThat(metadata.getString(CREATED_DATE_KEY)).isNotNull();
    assertThat(metadata.getString(CREATED_BY_USER_ID_KEY)).isNull();
    assertThat(metadata.getString(UPDATED_DATE_KEY)).isNotNull();
    assertThat(metadata.getString(UPDATED_BY_USER_ID_KEY)).isNull();
  }

  private static String queryAll() {
    return "?limit=100&query=" + URLEncoder.encode("cql.allRecords=1", StandardCharsets.UTF_8);
  }
}
