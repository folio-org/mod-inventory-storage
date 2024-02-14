package org.folio.rest.api;

import static org.folio.rest.support.http.InterfaceUrls.identifierTypesUrl;
import static org.folio.utility.ModuleUtility.getClient;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.junit.Before;
import org.junit.Test;

public class CancelledLccnMigrationTest extends MigrationTestBase {
  private static final String MIGRATION_SCRIPT = loadScript("addIdentifierTypeCancelledLCCN.sql");
  private static final Map<String, String> IDS = new HashMap<>();

  static {
    IDS.put("Cancelled LCCN", "c858e4f2-2b6b-4385-842b-60532ee34abb");
  }

  @SneakyThrows
  @Before
  public void beforeEach() {
    clearData();
    setupMaterialTypes();
    setupLoanTypes();
    setupLocations();
    removeAllEvents();
  }

  @Test
  public void canInsertIsmnAndUpcIdentifiers() throws Exception {
    executeMultipleSqlStatements(MIGRATION_SCRIPT);

    for (var identifier : IDS.entrySet()) {
      Response response = getIdentifier(identifier.getValue());
      assertThat(response.getStatusCode(), is(200));
      assertThat(response.getJson().getString("name"), is(identifier.getKey()));
    }
  }

  private Response getIdentifier(String id) throws Exception {
    CompletableFuture<Response> searchCompleted = new CompletableFuture<>();
    String url = identifierTypesUrl("/" + id).toString();
    getClient().get(url, TENANT_ID, ResponseHandler.json(searchCompleted));
    return searchCompleted.get(TIMEOUT, TimeUnit.SECONDS);
  }
}
