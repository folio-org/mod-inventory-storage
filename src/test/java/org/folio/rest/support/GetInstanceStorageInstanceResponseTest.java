package org.folio.rest.support;

import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.folio.HttpStatus.HTTP_BAD_REQUEST;
import static org.folio.HttpStatus.HTTP_INTERNAL_SERVER_ERROR;
import static org.folio.HttpStatus.HTTP_OK;
import static org.folio.HttpStatus.HTTP_UNAUTHORIZED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.folio.rest.jaxrs.model.Instances;
import org.junit.Test;

public class GetInstanceStorageInstanceResponseTest {

  @Test
  public void shouldRespond200WithApplicationJson() {
    var instances = new Instances();

    var response = GetInstanceStorageInstanceResponse.respond200WithApplicationJson(instances);

    assertNotNull(response);
    assertEquals(HTTP_OK.toInt(), response.getStatus());
    assertEquals(APPLICATION_JSON, response.getHeaders().getFirst(CONTENT_TYPE));
    assertEquals(instances, response.getEntity());
  }

  @Test
  public void shouldRespond400WithTextPlain() {
    var errorMessage = "Bad Request";

    var response = GetInstanceStorageInstanceResponse.respond400WithTextPlain(errorMessage);

    assertNotNull(response);
    assertEquals(HTTP_BAD_REQUEST.toInt(), response.getStatus());
    assertEquals(TEXT_PLAIN, response.getHeaders().getFirst(CONTENT_TYPE));
    assertEquals(errorMessage, response.getEntity());
  }

  @Test
  public void shouldRespond401WithTextPlain() {
    var errorMessage = "Unauthorized";

    var response = GetInstanceStorageInstanceResponse.respond401WithTextPlain(errorMessage);

    assertNotNull(response);
    assertEquals(HTTP_UNAUTHORIZED.toInt(), response.getStatus());
    assertEquals(TEXT_PLAIN, response.getHeaders().getFirst(CONTENT_TYPE));
    assertEquals(errorMessage, response.getEntity());
  }

  @Test
  public void shouldRespond500WithTextPlain() {
    var errorMessage = "Internal Server Error";

    var response = GetInstanceStorageInstanceResponse.respond500WithTextPlain(errorMessage);

    assertNotNull(response);
    assertEquals(HTTP_INTERNAL_SERVER_ERROR.toInt(), response.getStatus());
    assertEquals(TEXT_PLAIN, response.getHeaders().getFirst(CONTENT_TYPE));
    assertEquals(errorMessage, response.getEntity());
  }
}
