package org.folio.rest.api;

import static org.folio.rest.support.http.InterfaceUrls.modesOfIssuanceUrl;
import static org.hamcrest.CoreMatchers.is;

import io.vertx.core.json.JsonObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class ModesOfIssuanceMigrationScriptTest extends MigrationTestBase {
  private static final String MIGRATION_SCRIPT = loadScript("renameModesOfIssuance.sql");

  @Before
  public void beforeEach() {
    StorageTestSuite.deleteAll(modesOfIssuanceUrl(""));
  }

  @Test
  public void canMigrateModeOfIssuance() throws Exception {
    createModeOfIssuance("Integrating Resource", "rdamodeissue");
    createModeOfIssuance("Monograph", "rdamodeissue");
    createModeOfIssuance("Other", "rdamodeissue");
    createModeOfIssuance("Sequential Monograph", "rdamodeissue");
    createModeOfIssuance("Serial", "rdamodeissue");

    executeMultipleSqlStatements(MIGRATION_SCRIPT);

    List<JsonObject> modesOfIssuance = modesOfIssuanceClient.getAll();

    expectModeOfIssuance(modesOfIssuance.get(0), "integrating resource", "rdamodeissue");
    expectModeOfIssuance(modesOfIssuance.get(1), "multipart monograph", "rdamodeissue");
    expectModeOfIssuance(modesOfIssuance.get(2), "serial", "rdamodeissue");
    expectModeOfIssuance(modesOfIssuance.get(3), "single unit", "rdamodeissue");
    expectModeOfIssuance(modesOfIssuance.get(4), "unspecified", "folio");
  }

  private void expectModeOfIssuance(JsonObject modeOfIssuance, String name, String source) {
    Assert.assertThat(modeOfIssuance.getString("name"), is(name));
    Assert.assertThat(modeOfIssuance.getString("source"), is(source));
  }

  private void createModeOfIssuance(String name, String source)
    throws MalformedURLException, InterruptedException, ExecutionException, TimeoutException {
    modesOfIssuanceClient.create(new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("name", name)
      .put("source", source));
  }
}
