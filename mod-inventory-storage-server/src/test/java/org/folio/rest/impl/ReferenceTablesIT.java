package org.folio.rest.impl;

import static org.apache.http.HttpStatus.SC_OK;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.folio.dataimport.testsupport.tenant.TenantTestSupport;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Verifies that the reference-data JSON files under {@code ramls/reference-data/} actually load
 * during tenant install. This is orthogonal to every other {@code *IT} class: the shared
 * {@code TENANT_ID} tenant installs with {@code loadReference=false} specifically to skip this
 * (for speed/isolation), so this class installs its own, separate tenant with
 * {@code loadReference=true} on the same shared verticle instead of touching the default one.
 */
class ReferenceTablesIT extends BaseIntegrationTest {

  private static final String REFERENCE_TABLES_TENANT = "referencetables";
  private static final String TOTAL_RECORDS_KEY = "totalRecords";

  @BeforeAll
  static void installTenantWithReferenceData() {
    installTenant(REFERENCE_TABLES_TENANT, new TenantAttributes()
      .withModuleTo(MODULE_ID)
      .withParameters(TenantTestSupport.dataLoadingParameters(true, false)));
  }

  @BeforeEach
  void mockUserTenants() {
    mockUserTenantsForNonConsortiumMember(REFERENCE_TABLES_TENANT);
  }

  @ParameterizedTest(name = "should load exactly {2} {0} from reference data")
  @MethodSource("referenceTables")
  void shouldLoadReferenceRecords(String description, String path, int exactCount) {
    var totalRecords = getReferenceRecordCount(path);

    assertThat(totalRecords).as(description).isEqualTo(exactCount);
  }

  /**
   * Each expected count is the exact number of {@code *.json} files in the matching
   * {@code reference-data/<table>/} directory - one file loads exactly one record, so the two
   * must always match. If a count here goes out of sync with that directory, it means either a
   * file was added/removed without updating this list, or the loader stopped loading 1:1 - both
   * are worth investigating, not silently widening back to a range.
   */
  private static Stream<Arguments> referenceTables() {
    return Stream.concat(controlledVocabularyTables(), catalogingCodeTables());
  }

  private static Stream<Arguments> controlledVocabularyTables() {
    return Stream.of(
      Arguments.of("alternative title types", ResourcePaths.ALTERNATIVE_TITLE_TYPES, 13),
      Arguments.of("call number types", ResourcePaths.CALL_NUMBER_TYPES, 12),
      Arguments.of("classification types", ResourcePaths.CLASSIFICATION_TYPES, 10),
      Arguments.of("contributor name types", ResourcePaths.CONTRIBUTOR_NAME_TYPES, 3),
      Arguments.of("contributor types", ResourcePaths.CONTRIBUTOR_TYPES, 268),
      Arguments.of("electronic access relationship types", ResourcePaths.ELECTRONIC_ACCESS_RELATIONSHIPS, 5),
      Arguments.of("holdings note types", ResourcePaths.HOLDINGS_NOTE_TYPES, 7),
      Arguments.of("holdings types", ResourcePaths.HOLDINGS_TYPES, 5),
      Arguments.of("identifier types", ResourcePaths.IDENTIFIER_TYPES, 30),
      Arguments.of("ILL policies", ResourcePaths.ILL_POLICIES, 8),
      Arguments.of("instance formats", ResourcePaths.INSTANCE_FORMATS, 57),
      Arguments.of("nature-of-content terms", ResourcePaths.NATURE_OF_CONTENT_TERMS, 21),
      Arguments.of("instance statuses", ResourcePaths.INSTANCE_STATUSES, 6),
      Arguments.of("instance types (resource types)", ResourcePaths.INSTANCE_TYPES, 25),
      Arguments.of("item note types", ResourcePaths.ITEM_NOTE_TYPES, 7),
      Arguments.of("instance note types", ResourcePaths.INSTANCE_NOTE_TYPES, 53));
  }

  private static Stream<Arguments> catalogingCodeTables() {
    return Stream.of(
      Arguments.of("statistical code types", ResourcePaths.STATISTICAL_CODE_TYPES, 4),
      Arguments.of("statistical codes", ResourcePaths.STATISTICAL_CODES, 28),
      Arguments.of("loan types", ResourcePaths.LOAN_TYPES, 4),
      Arguments.of("holdings sources", ResourcePaths.HOLDINGS_SOURCES, 2),
      Arguments.of("instance relationship types", ResourcePaths.INSTANCE_RELATIONSHIP_TYPES, 3),
      Arguments.of("item damaged statuses", ResourcePaths.ITEM_DAMAGED_STATUSES, 2),
      Arguments.of("material types", ResourcePaths.MATERIAL_TYPES, 8),
      Arguments.of("modes of issuance", ResourcePaths.MODES_OF_ISSUANCE, 5));
  }

  private int getReferenceRecordCount(String path) {
    var response = get(doGet(client, path + queryAll(), REFERENCE_TABLES_TENANT));

    assertThat(response.status()).isEqualTo(SC_OK);
    return response.jsonBody().getInteger(TOTAL_RECORDS_KEY);
  }

  private static String queryAll() {
    return "?limit=400&query=" + URLEncoder.encode("cql.allRecords=1", StandardCharsets.UTF_8);
  }
}
