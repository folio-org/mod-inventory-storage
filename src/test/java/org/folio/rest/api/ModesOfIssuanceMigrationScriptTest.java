package org.folio.rest.api;

import static org.folio.rest.support.http.InterfaceUrls.modesOfIssuanceUrl;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import io.vertx.core.json.JsonObject;
import java.net.MalformedURLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import lombok.SneakyThrows;
import org.folio.rest.support.IndividualResource;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

public class ModesOfIssuanceMigrationScriptTest extends MigrationTestBase {
  private static final String MIGRATION_SCRIPT = loadScript("renameModesOfIssuance.sql");

  @AfterClass
  public static void afterAll() {
    // Prevent tests from other classes from being affected by this data.
    StorageTestSuite.deleteAll(modesOfIssuanceUrl(""));
  }

  @SneakyThrows
  @Before
  public void beforeEach() {
    clearData();
    setupMaterialTypes();
    setupLoanTypes();
    setupLocations();

    StorageTestSuite.deleteAll(modesOfIssuanceUrl(""));

    removeAllEvents();
  }

  @Test
  public void canMigrateModeOfIssuance() throws Exception {
    final IndividualResource integratingResource = createModeOfIssuance("4fc0f4fe-06fd-490a-a078-c4da1754e03a",
      "Integrating Resource", "rdamodeissue");
    final IndividualResource singleUnitResource = createModeOfIssuance("9d18a02f-5897-4c31-9106-c9abb5c7ae8b",
      "Monograph", "rdamodeissue");
    final IndividualResource unspecifiedResource = createModeOfIssuance("612bbd3d-c16b-4bfb-8517-2afafc60204a",
      "Other", "rdamodeissue");
    final IndividualResource multipartMonographResource = createModeOfIssuance("f5cc2ab6-bb92-4cab-b83f-5a3d09261a41",
      "Sequential Monograph", "rdamodeissue");
    final IndividualResource serialResource = createModeOfIssuance("068b5344-e2a6-40df-9186-1829e13cd344",
      "Serial", "rdamodeissue");

    executeMultipleSqlStatements(MIGRATION_SCRIPT);

    List<JsonObject> modesOfIssuance = modesOfIssuanceClient.getAll();

    expectModeOfIssuance(modesOfIssuance.get(0), integratingResource.getId(), "integrating resource", "rdamodeissue");
    expectModeOfIssuance(modesOfIssuance.get(1), multipartMonographResource.getId(), "multipart monograph",
      "rdamodeissue");
    expectModeOfIssuance(modesOfIssuance.get(2), serialResource.getId(), "serial", "rdamodeissue");
    expectModeOfIssuance(modesOfIssuance.get(3), singleUnitResource.getId(), "single unit", "rdamodeissue");
    expectModeOfIssuance(modesOfIssuance.get(4), unspecifiedResource.getId(), "unspecified", "folio");
  }

  private void expectModeOfIssuance(JsonObject modeOfIssuance, UUID id, String name, String source) {
    assertThat(modeOfIssuance.getString("id"), is(id.toString()));
    assertThat(modeOfIssuance.getString("name"), is(name));
    assertThat(modeOfIssuance.getString("source"), is(source));
  }

  private IndividualResource createModeOfIssuance(String id, String name, String source)
    throws MalformedURLException, InterruptedException, ExecutionException, TimeoutException {
    return modesOfIssuanceClient.create(new JsonObject()
      .put("id", id)
      .put("name", name)
      .put("source", source));
  }
}
