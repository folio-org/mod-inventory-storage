package org.folio.rest.support.messages.matchers;

import static org.folio.rest.support.JsonObjectMatchers.equalsIgnoringMetadata;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasProperty;

import java.net.URL;
import java.util.Map;

import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.support.messages.EventMessage;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matcher;
import org.jetbrains.annotations.NotNull;

import io.vertx.core.json.JsonObject;
import lombok.NonNull;
import lombok.Value;

@Value
public class EventMessageMatchers {
  @NonNull
  String expectedTenantId;
  @NonNull
  URL expectedUrl;

  @NotNull
  public Matcher<Iterable<? super EventMessage>> hasDeleteEventMessageFor(JsonObject representation) {
    return CoreMatchers.hasItem(allOf(
      isDeleteEvent(),
      isForTenant(),
      hasHeaders(),
      hasNoNewRepresentation(),
      hasOldRepresentation(representation)));
  }

  @NotNull
  public Matcher<EventMessage> isDeleteEvent() {
    return hasProperty("type", is("DELETE"));
  }

  @NotNull
  public Matcher<EventMessage> isForTenant() {
    return hasProperty("tenant", is(expectedTenantId));
  }

  @NotNull
  public Matcher<EventMessage> hasHeaders() {
    return hasProperty("headers", allOf(
      hasTenantHeader(),
      hasUrlHeader()));
  }

  @NotNull
  private Matcher<Map<? extends String, ? extends String>> hasUrlHeader() {
    return hasEntry(XOkapiHeaders.URL.toLowerCase(), expectedUrl.toString());
  }

  @NotNull
  private Matcher<Map<? extends String, ? extends String>> hasTenantHeader() {
    // Needs to be lower case because keys are mapped to lower case
    return hasEntry(XOkapiHeaders.TENANT.toLowerCase(), expectedTenantId);
  }

  @NotNull
  public Matcher<EventMessage> hasOldRepresentation(JsonObject expectedRepresentation) {
    // ignore metadata because created and updated date might be represented
    // with either +00:00 or Z due to differences in serialization / deserialization
    return hasProperty("oldRepresentation",
      equalsIgnoringMetadata(expectedRepresentation));
  }

  @NotNull
  public Matcher<EventMessage> hasNoNewRepresentation() {
    return hasProperty("newRepresentation", is(nullValue()));
  }
}
