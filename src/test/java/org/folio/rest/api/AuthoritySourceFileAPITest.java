package org.folio.rest.api;

import static org.folio.HttpStatus.HTTP_BAD_REQUEST;
import static org.folio.rest.impl.AuthoritySourceFileAPI.DELETE_PRE_DEFINED_SOURCE_FILE_ERROR;
import static org.folio.rest.impl.AuthoritySourceFileAPI.PRE_DEFINED_SOURCE_FILE_IDS;
import static org.folio.rest.impl.AuthoritySourceFileAPI.UPDATE_PRE_DEFINED_SOURCE_FILE_ERROR;
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
  public void shouldNotDeletePreDefinedSourceFile() {
    var id = PRE_DEFINED_SOURCE_FILE_IDS.stream().findAny().orElseThrow();
    var response =
      authoritySourceFileClient.attemptToDelete(UUID.fromString(id));

    assertEquals(HTTP_BAD_REQUEST.toInt(), response.getStatusCode());
    assertEquals(DELETE_PRE_DEFINED_SOURCE_FILE_ERROR, response.getBody());
  }

  @Test
  public void shouldNotUpdatePreDefinedSourceFile() {
    var id = PRE_DEFINED_SOURCE_FILE_IDS.stream().findAny().orElseThrow();
    var entity = new JsonObject().put("id", id)
      .put("codes", List.of("tst"))
      .put("type", "Subjects")
      .put("name", "test");

    var response = authoritySourceFileClient.attemptToReplace(id, entity);

    assertEquals(HTTP_BAD_REQUEST.toInt(), response.getStatusCode());
    assertEquals(UPDATE_PRE_DEFINED_SOURCE_FILE_ERROR, response.getBody());
  }

}
