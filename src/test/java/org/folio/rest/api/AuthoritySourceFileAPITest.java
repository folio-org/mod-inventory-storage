package org.folio.rest.api;

import static org.folio.HttpStatus.HTTP_BAD_REQUEST;
import static org.folio.HttpStatus.HTTP_NOT_FOUND;
import static org.folio.rest.api.entities.AuthoritySourceFile.CODE_KEY;
import static org.folio.rest.api.entities.AuthoritySourceFile.NAME_KEY;
import static org.folio.rest.api.entities.AuthoritySourceFile.TYPE_KEY;
import static org.folio.rest.impl.AuthoritySourceFileAPI.DELETE_PRE_DEFINED_SOURCE_FILE_ERROR;
import static org.folio.rest.impl.AuthoritySourceFileAPI.PRE_DEFINED_SOURCE_FILE_IDS;
import static org.folio.rest.impl.AuthoritySourceFileAPI.UPDATE_PRE_DEFINED_SOURCE_FILE_ERROR;
import static org.junit.Assert.assertEquals;

import io.vertx.core.json.JsonObject;
import java.util.UUID;
import junitparams.JUnitParamsRunner;
import org.folio.rest.support.http.ResourceClient;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class AuthoritySourceFileAPITest extends TestBase {

  private static ResourceClient authoritySourceFileClient = ResourceClient.forAuthoritySourceFiles(client);

  @Test
  public void shouldCreateUpdateAndDeleteSourceFile() {
    var id = UUID.randomUUID();
    var newSourceFile = new JsonObject().put("id", id)
      .put(CODE_KEY, "tst")
      .put(TYPE_KEY, "Subjects")
      .put(NAME_KEY, "new source file");

    var storedSourceFile = authoritySourceFileClient.create(newSourceFile).getJson();

    storedSourceFile.put(NAME_KEY, "updated source file");

    authoritySourceFileClient.replace(id, storedSourceFile);

    var updatedSourceFile = authoritySourceFileClient.getById(id).getJson();
    assertEquals("tst", updatedSourceFile.getString(CODE_KEY));
    assertEquals("Subjects", updatedSourceFile.getString(TYPE_KEY));
    assertEquals("updated source file", updatedSourceFile.getString(NAME_KEY));

    authoritySourceFileClient.delete(id);

    var response = authoritySourceFileClient.getById(id);
    assertEquals(HTTP_NOT_FOUND.toInt(), response.getStatusCode());
  }

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
      .put("code", "tst")
      .put("type", "Subjects")
      .put("name", "test");

    var response = authoritySourceFileClient.attemptToReplace(id, entity);

    assertEquals(HTTP_BAD_REQUEST.toInt(), response.getStatusCode());
    assertEquals(UPDATE_PRE_DEFINED_SOURCE_FILE_ERROR, response.getBody());
  }

}
