package org.folio.rest.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.core.Response;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResponseHandlerUtilTest {

  private static final int SC_BAD_REQUEST = 400;
  private static final int SC_UNPROCESSABLE = 422;

  @Test
  @DisplayName("should return the response unchanged when the entity is null")
  void shouldReturnResponseUnchanged_whenEntityIsNull() {
    var response = Response.status(SC_BAD_REQUEST).build();

    assertThat(ResponseHandlerUtil.handleHridError(response)).isSameAs(response);
    assertThat(ResponseHandlerUtil.handleHridErrorInInstance(response)).isSameAs(response);
  }

  @Test
  @DisplayName("should return the response unchanged when the errors list is empty")
  void shouldReturnResponseUnchanged_whenErrorsListIsEmpty() {
    var response = Response.status(SC_UNPROCESSABLE).entity(new Errors().withErrors(new ArrayList<>())).build();

    assertThat(ResponseHandlerUtil.handleHridError(response)).isSameAs(response);
    assertThat(ResponseHandlerUtil.handleHridErrorInInstance(response)).isSameAs(response);
  }

  @Test
  @DisplayName("should return the response unchanged when the error has no message")
  void shouldReturnResponseUnchanged_whenErrorHasNoMessage() {
    var errors = new Errors().withErrors(List.of(new Error()));
    var response = Response.status(SC_UNPROCESSABLE).entity(errors).build();

    assertThat(ResponseHandlerUtil.handleHridError(response)).isSameAs(response);
    assertThat(ResponseHandlerUtil.handleHridErrorInInstance(response)).isSameAs(response);
  }

  @Test
  @DisplayName("should rewrite the hrid index name in the message when the text error is an hrid conflict")
  void shouldRewriteHridIndexName_whenTextErrorIsHridConflict() {
    var message = "lower(f_unaccent(jsonb ->> 'hrid'::text)) value already exists: (hrid)=(in1)";
    var response = Response.status(SC_BAD_REQUEST).entity(message).build();

    var result = ResponseHandlerUtil.handleHridError(response);

    assertThat(result.getEntity()).isEqualTo("HRID value already exists: (hrid)=(in1)");
  }
}
