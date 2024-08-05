package org.folio.rest.support.messages.matchers;

import static org.folio.rest.support.JsonObjectMatchers.equalsIgnoringMetadata;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasProperty;

import io.vertx.core.json.JsonObject;
import java.net.URL;
import java.util.Map;
import lombok.NonNull;
import lombok.Value;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.support.messages.EventMessage;
import org.hamcrest.Matcher;
import org.jetbrains.annotations.NotNull;

@Value
public class EventMessageMatchers {
  @NonNull
  String expectedTenantId;
  @NonNull
  URL expectedUrl;

  @NotNull
  private static Matcher<EventMessage> hasType(String type) {
    return hasProperty("type", is(type));
  }

  @NotNull
  private static Matcher<EventMessage> hasOldRepresentationThat(Matcher<?> matcher) {
    return hasProperty("oldRepresentation", matcher);
  }

  @NotNull
  private static Matcher<EventMessage> hasNewRepresentationThat(Matcher<?> matcher) {
    return hasProperty("newRepresentation", matcher);
  }

  @NotNull
  public Matcher<Iterable<? super EventMessage>> hasCreateEventMessageFor(JsonObject representation) {
    return hasCreateEventMessageFor(representation, expectedTenantId, expectedUrl.toString());
  }

  @NotNull
  public Matcher<Iterable<? super EventMessage>> hasCreateEventMessageFor(JsonObject representation,
                                                                          String expectedTenantId,
                                                                          String okapiUrlExpected) {
    return hasItem(allOf(
      isCreateEvent(),
      isForTenant(expectedTenantId),
      hasHeaders(expectedTenantId, okapiUrlExpected),
      hasNewRepresentation(representation),
      hasNoOldRepresentation()));
  }

  @NotNull
  public Matcher<Iterable<? super EventMessage>> hasUpdateEventMessageFor(JsonObject oldRepresentation,
                                                                          JsonObject newRepresentation) {

    return hasUpdateEventMessageFor(oldRepresentation, newRepresentation, expectedUrl.toString());
  }

  @NotNull
  public Matcher<Iterable<? super EventMessage>> hasUpdateEventMessageFor(JsonObject oldRepresentation,
                                                                          JsonObject newRepresentation,
                                                                          String okapiUrlExpected) {

    return hasItem(allOf(
      isUpdateEvent(),
      isForTenant(),
      hasHeaders(expectedTenantId, okapiUrlExpected),
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
  public Matcher<Iterable<? super EventMessage>> hasReindexEventMessageFor(JsonObject representation) {
    return hasReindexEventMessageFor(representation, expectedTenantId, expectedUrl.toString());
  }

  @NotNull
  public Matcher<Iterable<? super EventMessage>> hasReindexEventMessageFor(JsonObject representation,
                                                                           String expectedTenantId,
                                                                           String okapiUrlExpected) {
    return hasItem(allOf(
      isReindexEvent(),
      isForTenant(expectedTenantId),
      hasHeaders(expectedTenantId, okapiUrlExpected),
      hasNoOldRepresentation(),
      hasNewRepresentation(representation)));
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
  public Matcher<EventMessage> isUpdateEvent() {
    return hasType("UPDATE");
  }

  @NotNull
  public Matcher<EventMessage> isReindexEvent() {
    return hasType("REINDEX");
  }

  @NotNull
  public Matcher<EventMessage> isForTenant() {
    return isForTenant(expectedTenantId);
  }

  @NotNull
  public Matcher<EventMessage> isForTenant(String tenantIdExpected) {
    return hasProperty("tenant", is(tenantIdExpected));
  }

  @NotNull
  public Matcher<EventMessage> hasHeaders() {
    return hasProperty("headers", allOf(
      hasTenantHeader(),
      hasUrlHeader()));
  }

  @NotNull
  public Matcher<EventMessage> hasHeaders(String tenantIdExpected, String okapiUrlExpected) {
    return hasProperty("headers", allOf(
      hasTenantHeader(tenantIdExpected),
      hasUrlHeader(okapiUrlExpected)));
  }

  @NotNull
  public Matcher<EventMessage> hasNewRepresentation(
    JsonObject expectedRepresentation) {
    return hasNewRepresentationThat(equalsIgnoringMetadata(expectedRepresentation));
  }

  @NotNull
  private Matcher<EventMessage> isCreateEvent() {
    return hasType("CREATE");
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
  private Matcher<Map<? extends String, ? extends String>> hasUrlHeader() {
    return hasUrlHeader(expectedUrl.toString());
  }

  @NotNull
  private Matcher<Map<? extends String, ? extends String>> hasUrlHeader(String okapiUrlExpected) {
    return hasEntry(XOkapiHeaders.URL.toLowerCase(), okapiUrlExpected.toLowerCase());
  }

  @NotNull
  private Matcher<Map<? extends String, ? extends String>> hasTenantHeader() {
    return hasTenantHeader(expectedTenantId);
  }

  @NotNull
  private Matcher<Map<? extends String, ? extends String>> hasTenantHeader(String tenantIdExpected) {
    // Needs to be lower case because keys are mapped to lower case
    return hasEntry(XOkapiHeaders.TENANT.toLowerCase(), tenantIdExpected);
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
  private Matcher<EventMessage> hasNoNewRepresentation() {
    return hasNewRepresentationThat(is(nullValue()));
  }
}
