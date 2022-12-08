package org.folio.rest.support.messages.matchers;

import static org.folio.rest.support.JsonObjectMatchers.equalsIgnoringMetadata;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;

import java.net.URL;
import java.util.Collection;
import java.util.Map;

import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.support.messages.EventMessage;
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
  public Matcher<Iterable<? super EventMessage>> hasCreateEventMessageFor(JsonObject representation) {
    return hasItem(allOf(
      isCreateEvent(),
      isForTenant(),
      hasHeaders(),
      hasNewRepresentation(representation),
      hasNoOldRepresentation()));
  }

  @NotNull
  public Matcher<Collection<?>> hasCreateEventMessagesFor(
    Collection<JsonObject> representations) {

    return hasSize(representations.size());
  }

  @NotNull
  public Matcher<Iterable<? super EventMessage>> hasUpdateEventMessageFor(JsonObject oldRepresentation,
    JsonObject newRepresentation) {

    return hasItem(allOf(
      isUpdateEvent(),
      isForTenant(),
      hasHeaders(),
      hasNewRepresentation(newRepresentation),
      hasOldRepresentation(oldRepresentation)));
  }

  @NotNull
  public Matcher<Iterable<? super EventMessage>> hasNoUpdateEventMessage() {
    return not(hasItem(isUpdateEvent()));
  }

  @NotNull
  public Matcher<Iterable<? super EventMessage>> hasDeleteEventMessageFor(JsonObject representation) {
    return hasItem(allOf(
      isDeleteEvent(),
      isForTenant(),
      hasHeaders(),
      hasNoNewRepresentation(),
      hasOldRepresentation(representation)));
  }

  @NotNull
  public Matcher<Iterable<? super EventMessage>> hasNoDeleteEventMessage() {
    return not(hasItem(isDeleteEvent()));
  }

  @NotNull
  public Matcher<Iterable<? super EventMessage>> hasDeleteAllEventMessage() {
    return hasItem(allOf(
      isDeleteAllEvent(),
      isForTenant(),
      hasHeaders(),
      hasNoNewRepresentation(),
      hasNoOldRepresentation()));
  }

  @NotNull
  private Matcher<EventMessage> isCreateEvent() {
    return hasType("CREATE");
  }

  @NotNull
  private Matcher<EventMessage> isUpdateEvent() {
    return hasType("UPDATE");
  }

  @NotNull
  private Matcher<EventMessage> isDeleteEvent() {
    return hasType("DELETE");
  }

  @NotNull
  private Matcher<EventMessage> isDeleteAllEvent() {
    return hasType("DELETE_ALL");
  }

  @NotNull
  private static Matcher<EventMessage> hasType(String type) {
    return hasProperty("type", is(type));
  }

  @NotNull
  private Matcher<EventMessage> isForTenant() {
    return hasProperty("tenant", is(expectedTenantId));
  }

  @NotNull
  private Matcher<EventMessage> hasHeaders() {
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
  private Matcher<EventMessage> hasOldRepresentation(JsonObject expectedRepresentation) {
    return hasOldRepresentationThat(equalsIgnoringMetadata(expectedRepresentation));
  }

  @NotNull
  private Matcher<EventMessage> hasNoOldRepresentation() {
    return hasOldRepresentationThat(is(nullValue()));
  }

  @NotNull
  private static Matcher<EventMessage> hasOldRepresentationThat(Matcher<?> matcher) {
    return hasProperty("oldRepresentation", matcher);
  }

  @NotNull
  private Matcher<EventMessage> hasNewRepresentation(JsonObject expectedRepresentation) {
    return hasNewRepresentationThat(equalsIgnoringMetadata(expectedRepresentation));
  }

  @NotNull
  private Matcher<EventMessage> hasNoNewRepresentation() {
    return hasNewRepresentationThat(is(nullValue()));
  }

  @NotNull
  private static Matcher<EventMessage> hasNewRepresentationThat(Matcher<?> matcher) {
    return hasProperty("newRepresentation", matcher);
  }
}
