package org.folio.rest.api;

import static org.folio.rest.api.entities.AuthoritySourceFile.BASE_URL_KEY;
import static org.folio.rest.api.entities.AuthoritySourceFile.CODES_KEY;
import static org.folio.rest.api.entities.AuthoritySourceFile.NAME_KEY;
import static org.folio.rest.api.entities.AuthoritySourceFile.SOURCE_KEY;
import static org.folio.rest.api.entities.AuthoritySourceFile.TYPE_KEY;
import static org.folio.rest.api.entities.JsonEntity.ID_KEY;
import static org.folio.utility.ModuleUtility.getClient;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.junit.Assert.assertEquals;

import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import junitparams.JUnitParamsRunner;
import org.folio.rest.support.http.InterfaceUrls;
import org.folio.rest.support.http.ResourceClient;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class AuthoritySourceFileApiTest extends TestBase {

  private final ResourceClient authoritySourceFileClient = ResourceClient.forAuthoritySourceFiles(getClient());

  private static JsonObject prepareTestEntity(UUID id, String baseUrl) {
    return new JsonObject()
      .put(ID_KEY, id)
      .put(CODES_KEY, List.of("tst1"))
      .put(TYPE_KEY, "Subjects")
      .put(SOURCE_KEY, "local")
      .put(BASE_URL_KEY, baseUrl)
      .put(NAME_KEY, "test");
  }

  @Test
  public void shouldNormalizeBaseUrl()
    throws ExecutionException, InterruptedException, TimeoutException {
    var nonNormalizedBaseUrl = "https://www.id.loc.gov/authorities/test-source";
    var entityId = UUID.randomUUID();

    var creationResponse = authoritySourceFileClient.create(prepareTestEntity(entityId, nonNormalizedBaseUrl));
    var entity = creationResponse.getJson();

    assertEquals("id.loc.gov/authorities/test-source/", entity.getString(BASE_URL_KEY));

    // Test baseUrl normalization on PUT
    entity.put(BASE_URL_KEY, "http://example.com/authorities/");

    authoritySourceFileClient.replace(entityId, entity);
    var updatedEntity = authoritySourceFileClient.getById(entityId).getJson();

    assertEquals("example.com/authorities/", updatedEntity.getString(BASE_URL_KEY));

    // Test baseUrl normalization on PATCH
    var patchData = new JsonObject().put(BASE_URL_KEY, "http://example.com/authorities/patched");

    getClient().patch(InterfaceUrls.authoritySourceFilesUrl("/" + entityId), patchData, TENANT_ID)
      .get(20, TimeUnit.SECONDS);

    var patchedEntity = authoritySourceFileClient.getById(entityId).getJson();

    assertEquals("example.com/authorities/patched/", patchedEntity.getString(BASE_URL_KEY));
  }

}
