package org.folio.rest.support;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import javax.ws.rs.core.Response;

import org.folio.rest.testing.UtilityClassTester;
import org.junit.Test;

public class ResponseUtilTest {
  @Test
  public void canReturnFalseIfNullResponse() {
    assertFalse(ResponseUtil.hasCreatedStatus(null));
  }

  @Test
  public void canReturnTrueIfCreatedStatus() {
    Response response = Response.status(Response.Status.CREATED).build();

    assertTrue(ResponseUtil.hasCreatedStatus(response));
  }

  @Test
  public void canReturnFalseIfNotCreatedStatus() {
    Response response = Response.status(Response.Status.ACCEPTED).build();

    assertFalse(ResponseUtil.hasCreatedStatus(response));
  }

  @Test
  public void isUtilityClass(){
    UtilityClassTester.assertUtilityClass(ResponseUtil.class);
  }

  @Test
  public void returnNullIfResponseNull() {
    assertNull(ResponseUtil.copyResponseWithNewEntity(null, "entity"));
  }

  @Test
  public void copyResponseWithNewEntity() {
    final String locationHeaderName = "location";
    final String locationHeaderValue = "location-to-follow";
    final String newEntityValue = "new-entity";

    Response originResponse = Response.status(Response.Status.ACCEPTED)
      .entity("old")
      .header(locationHeaderName, locationHeaderValue)
      .build();

    Response newResponse = ResponseUtil
      .copyResponseWithNewEntity(originResponse, newEntityValue);

    assertThat(newResponse.getEntity(), is(newEntityValue));
    assertThat(newResponse.getHeaderString(locationHeaderName), is(locationHeaderValue));
    assertThat(newResponse.getStatusInfo(), is(Response.Status.ACCEPTED));
  }
}
