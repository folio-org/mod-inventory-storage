package org.folio.rest.support;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Constructor;

import javax.ws.rs.core.Response;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ResponseUtilTest {
  @Rule
  public ExpectedException expectedException = ExpectedException.none();

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
  public void canNotInstantiateClass() throws Exception {
    final Constructor<ResponseUtil> constructor = ResponseUtil.class
      .getDeclaredConstructor();

    constructor.setAccessible(true);

    expectedException.expectCause(instanceOf(UnsupportedOperationException.class));
    constructor.newInstance();
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
