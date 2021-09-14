package org.folio.rest.api;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.folio.rest.support.http.InterfaceUrls.identifierTypesUrl;
import static org.folio.rest.api.StorageTestSuite.TENANT_ID;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.rest.support.Response;

import org.folio.rest.support.ResponseHandler;
import org.junit.Test;

import io.vertx.core.json.JsonObject;

public class UpcIsmnMigrationScriptTest extends MigrationTestBase {
  private static final String MIGRATION_SCRIPT = loadScript("addIdentifierTypesUpcIsmn.sql");
  private static final HashMap<String,String> ids = new HashMap<String, String>();
  static{
    ids.put("UPC","1795ea23-6856-48a5-a772-f356e16a8a6c");
    ids.put("Invalid UPC", "b3ea81fb-3324-4c64-9efc-7c0c93d5943c");
    ids.put("ISMN", "ebfd00b6-61d3-4d87-a6d8-810c941176d5");
    ids.put("Invalid ISMN", "ebfd00b6-61d3-4d87-a6d8-810c941176d5");
  }

  @Test
  public void canInsertIsmnAndUpcIdentifiers() throws Exception  {

    executeMultipleSqlStatements(MIGRATION_SCRIPT);

    JsonObject response = getIdentifierTypes().getJson();

    for (var identifier: ids.entrySet()) {
      assertThat(response.getString(identifier.getKey()), is(identifier.getValue()));

    }
  }

  private Response getIdentifierTypes() throws Exception  {
    CompletableFuture<Response> searchCompleted = new CompletableFuture<>();
    String url = identifierTypesUrl("?limit=100").toString();
    client.get(url, TENANT_ID, ResponseHandler.json(searchCompleted));
    Response searchResponse = searchCompleted.get(5, TimeUnit.SECONDS);
    return searchResponse;
  }
}
