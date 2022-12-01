package org.folio.rest.api;

import static org.folio.rest.support.http.InterfaceUrls.identifierTypesUrl;
import static org.folio.utility.ModuleUtility.getClient;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.junit.Before;
import org.junit.Test;

public class UpcIsmnMigrationScriptTest extends MigrationTestBase {
  private static final String MIGRATION_SCRIPT = loadScript("addIdentifierTypesUpcIsmn.sql");
  private static final HashMap<String,String> ids = new HashMap<String, String>();
  static{
    ids.put("UPC","1795ea23-6856-48a5-a772-f356e16a8a6c");
    ids.put("Invalid UPC", "b3ea81fb-3324-4c64-9efc-7c0c93d5943c");
    ids.put("ISMN", "ebfd00b6-61d3-4d87-a6d8-810c941176d5");
    ids.put("Invalid ISMN", "4f07ea37-6c7f-4836-add2-14249e628ed1");
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
  public void canInsertIsmnAndUpcIdentifiers() throws Exception  {

    executeMultipleSqlStatements(MIGRATION_SCRIPT);

    for (var identifier: ids.entrySet()) {
      Response response = getIdentifier(identifier.getValue());
      assertThat(response.getStatusCode(), is(200));
      assertThat(response.getJson().getString("name"), is(identifier.getKey()));
    }
  }

  private Response getIdentifier(String id) throws Exception  {
    CompletableFuture<Response> searchCompleted = new CompletableFuture<>();
    String url = identifierTypesUrl("/" + id).toString();
    getClient().get(url, TENANT_ID, ResponseHandler.json(searchCompleted));
    Response searchResponse = searchCompleted.get(10, TimeUnit.SECONDS);
    return searchResponse;
  }
}
