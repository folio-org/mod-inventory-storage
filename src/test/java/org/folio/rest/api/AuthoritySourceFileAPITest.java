package org.folio.rest.api;

import static org.folio.rest.api.entities.AuthoritySourceFile.BASE_URL_KEY;
import static org.folio.rest.api.entities.AuthoritySourceFile.CODES_KEY;
import static org.folio.rest.api.entities.AuthoritySourceFile.NAME_KEY;
import static org.folio.rest.api.entities.AuthoritySourceFile.SOURCE_KEY;
import static org.folio.rest.api.entities.AuthoritySourceFile.TYPE_KEY;
import static org.folio.rest.api.entities.JsonEntity.ID_KEY;
import static org.junit.Assert.assertEquals;

import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.UUID;
import junitparams.JUnitParamsRunner;
import org.folio.rest.support.http.ResourceClient;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class AuthoritySourceFileAPITest extends TestBase {

  private static ResourceClient authoritySourceFileClient = ResourceClient.forAuthoritySourceFiles(client);

  @Test
  public void shouldNormalizeBaseUrl() {
    var nonNormalizedBaseUrl = "https://www.id.loc.gov/authoritiesq/subjects";
    var entityId = UUID.randomUUID();

    var creationResponse = authoritySourceFileClient.create(
      prepareTestEntity(entityId, nonNormalizedBaseUrl));
    var entity = creationResponse.getJson();

    assertEquals("id.loc.gov/authoritiesq/subjects/", entity.getString(BASE_URL_KEY));

    // Test baseUrl normalization on PUT
    entity.put(BASE_URL_KEY, "http://example.com/authorities/");

    authoritySourceFileClient.replace(entityId, entity);
    var updatedEntity = authoritySourceFileClient.getById(entityId).getJson();

    assertEquals("example.com/authorities/", updatedEntity.getString(BASE_URL_KEY));
  }

  private static JsonObject prepareTestEntity(UUID id, String baseUrl) {
    return new JsonObject()
      .put(ID_KEY, id)
      .put(CODES_KEY, List.of("tst1"))
      .put(TYPE_KEY, "Subjects")
      .put(SOURCE_KEY, "local")
      .put(BASE_URL_KEY, baseUrl)
      .put(NAME_KEY, "test");
  }

}
