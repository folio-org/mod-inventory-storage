package org.folio.it.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.HttpStatus.SC_CREATED;
import static org.folio.HttpStatus.SC_NOT_FOUND;
import static org.folio.HttpStatus.SC_NO_CONTENT;
import static org.folio.HttpStatus.SC_OK;
import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;
import static org.folio.it.InstanceStorageFixtures.createInstanceType;
import static org.folio.support.ResourcePaths.CONTRIBUTOR_NAME_TYPES;
import static org.folio.support.ResourcePaths.IDENTIFIER_TYPES;
import static org.folio.support.ResourcePaths.INSTANCES;
import static org.folio.support.ResourcePaths.INSTANCES_SYNC;
import static org.folio.support.ResourcePaths.INSTANCE_STATUSES;
import static org.folio.support.ResourcePaths.SUBJECT_SOURCES;
import static org.folio.support.ResourcePaths.SUBJECT_TYPES;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.folio.it.BaseIntegrationTest;
import org.folio.rest.jaxrs.model.MarcJson;
import org.folio.support.messages.InstanceEventMessageChecks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

/**
 * Shared reference data, lifecycle, and request/assertion helpers for the
 * {@code InstanceStorage*IT} classes (split by feature from the former monolithic
 * {@code InstanceStorageIT} - see docs/test-quality-improvement-plan.md WS5). Only helpers
 * used by two or more of the split classes live here; a helper used by exactly one split
 * class stays local to that class instead.
 */
abstract class InstanceStorageTestBase extends BaseIntegrationTest {

  static final String INSTANCES_KEY = "instances";
  static final String TOTAL_RECORDS_KEY = "totalRecords";
  static final String METADATA_KEY = "metadata";
  static final String SUBJECTS_KEY = "subjects";
  static final String TAG_VALUE = "test-tag";
  static final String STATUS_UPDATED_DATE_PROPERTY = "statusUpdatedDate";
  static final String DISCOVERY_SUPPRESS = "discoverySuppress";
  static final String STATISTICAL_CODE_IDS_KEY = "statisticalCodeIds";
  static final String INVALID_VALUE = "invalid value";
  static final String INVALID_TYPE_ERROR_MESSAGE =
    "invalid input syntax for type uuid: \"" + INVALID_VALUE + "\"";
  // Reference data the legacy rest.api stack loads from reference-data/ at tenant install
  // (loadReference=true); the shared verticle's tenant doesn't, so this class seeds its own
  // copies once, with fresh ids - nothing in this class depends on the literal reference-data
  // UUIDs, only on referential consistency within its own fixtures.
  static String instanceTypeId;
  static String isbnTypeId;
  static String asinTypeId;
  static String invalidIsbnTypeId;
  static String personalNameTypeId;
  static String catalogedStatusId;
  static String otherStatusId;
  static String subjectSourceId;
  static String subjectTypeId;

  // The "isbn"/"invalidIsbn" CQL indexes are backed by normalize_isbns()/normalize_invalid_isbns()
  // Postgres functions (see create_isbn_functions.sql) that hardcode these exact reference-data
  // ids - unlike every other type seeded below, these two cannot be fresh/random.
  private static final String ISBN_TYPE_ID = "8261054f-be78-422d-bd51-4ed9f33c3422";
  private static final String INVALID_ISBN_TYPE_ID = "fcca2643-406a-482a-b760-7a7f8aec640e";

  final InstanceEventMessageChecks instanceMessageChecks = eventMessageChecks();
  final MarcJson marcJson = new MarcJson()
    .withLeader("xxxxxnam a22yyyyy c 4500")
    .withFields(List.of(
      new JsonObject().put("001", "029857716"),
      new JsonObject().put("245", new JsonObject().put("ind1", "0").put("ind2", "4")
        .put("subfields", new JsonArray().add(new JsonObject().put("a", "The Yearbook of Okapiology"))))));

  @BeforeAll
  static void seedReferenceData() {
    instanceTypeId = createInstanceType(client);
    isbnTypeId = createIdentifierType(ISBN_TYPE_ID, "ISBN");
    asinTypeId = createIdentifierType(null, "ASIN");
    invalidIsbnTypeId = createIdentifierType(INVALID_ISBN_TYPE_ID, "Invalid ISBN");
    personalNameTypeId = createContributorNameType("Personal name");
    catalogedStatusId = createInstanceStatus("cat", "Cataloged");
    otherStatusId = createInstanceStatus("other", "Other");
    subjectSourceId = await(doPost(client, SUBJECT_SOURCES,
      new JsonObject().put("name", "a subject source").put("source", "local"))).jsonBody().getString("id");
    subjectTypeId = await(doPost(client, SUBJECT_TYPES,
      new JsonObject().put("name", "a subject type").put("source", "local"))).jsonBody().getString("id");
  }

  @BeforeEach
  void clearInstances() {
    runQuery("TRUNCATE TABLE instance, holdings_record, item CASCADE");
  }

  @AfterEach
  void resetHridSequence() {
    setInstanceSequence(1);
  }

  private static String createIdentifierType(String id, String name) {
    var request = new JsonObject().put("name", name).put("source", "folio");
    if (id != null) {
      request.put("id", id);
    }
    var response = await(doPost(client, IDENTIFIER_TYPES, request));
    return response.jsonBody().getString("id");
  }

  private static String createContributorNameType(String name) {
    var response = await(doPost(client, CONTRIBUTOR_NAME_TYPES, new JsonObject().put("name", name)));
    return response.jsonBody().getString("id");
  }

  private static String createInstanceStatus(String code, String name) {
    var response = await(doPost(client, INSTANCE_STATUSES,
      new JsonObject().put("code", code).put("name", name).put("source", "folio")));
    return response.jsonBody().getString("id");
  }

  static JsonObject identifier(String identifierTypeId, String value) {
    return new JsonObject().put("identifierTypeId", identifierTypeId).put("value", value);
  }

  static JsonObject contributor(String contributorNameTypeId, String name) {
    return new JsonObject().put("contributorNameTypeId", contributorNameTypeId).put("name", name);
  }

  static JsonObject instanceRequest(UUID id, String source, String title, JsonArray identifiers,
                                     JsonArray contributors, JsonArray tags) {
    var instance = new JsonObject();
    if (id != null) {
      instance.put("id", id.toString());
    }
    instance.put("title", title);
    instance.put("source", source);
    instance.put("identifiers", identifiers);
    instance.put("contributors", contributors);
    instance.put("instanceTypeId", instanceTypeId);
    instance.put("tags", new JsonObject().put("tagList", tags));
    instance.put("_version", 1);
    return instance;
  }

  static JsonObject smallAngryPlanet(UUID id) {
    return instanceRequest(id, "MARC", "Long Way to a Small Angry Planet",
      new JsonArray().add(identifier(isbnTypeId, "9781473619777")),
      new JsonArray().add(contributor(personalNameTypeId, "Chambers, Becky")), new JsonArray().add(TAG_VALUE));
  }

  static JsonObject nod(UUID id) {
    return instanceRequest(id, "MARC", "Nod",
      new JsonArray().add(identifier(asinTypeId, "B01D1PLMDO")),
      new JsonArray().add(contributor(personalNameTypeId, "Barnes, Adrian")), new JsonArray().add(TAG_VALUE));
  }

  static JsonObject uprooted(UUID id) {
    return instanceRequest(id, "MARC", "Uprooted",
      new JsonArray().add(identifier(isbnTypeId, "1447294149")).add(identifier(isbnTypeId, "9781447294146")),
      new JsonArray().add(contributor(personalNameTypeId, "Novik, Naomi")), new JsonArray().add(TAG_VALUE));
  }

  static JsonObject temeraire(UUID id) {
    return instanceRequest(id, "MARC", "Temeraire",
      new JsonArray().add(identifier(isbnTypeId, "0007258712")).add(identifier(isbnTypeId, "9780007258710")),
      new JsonArray().add(contributor(personalNameTypeId, "Novik, Naomi")), new JsonArray().add(TAG_VALUE));
  }

  private static JsonObject interestingTimes(UUID id) {
    return instanceRequest(id, "MARC", "Interesting Times",
      new JsonArray().add(identifier(isbnTypeId, "0552167541")).add(identifier(isbnTypeId, "978-0-552-16754-3"))
        .add(identifier(invalidIsbnTypeId, "1-2-3-4-5")),
      new JsonArray().add(contributor(personalNameTypeId, "Pratchett, Terry")), new JsonArray().add(TAG_VALUE));
  }

  static JsonObject createInstance(JsonObject instanceToCreate) {
    var response = await(doPost(client, INSTANCES, instanceToCreate));
    assertThat(response.status()).isEqualTo(SC_CREATED);
    return response.jsonBody();
  }

  static TestResponse syncBatch(JsonArray instancesArray) {
    return await(doPost(client, INSTANCES_SYNC, new JsonObject().put(INSTANCES_KEY, instancesArray)));
  }

  static TestResponse update(JsonObject instance) {
    var id = instance.getString("id");
    return await(doPut(client, INSTANCES + "/" + id, instance));
  }

  static JsonObject updateInstance(JsonObject instance) {
    var putResponse = update(instance);
    assertThat(putResponse.status()).isEqualTo(SC_NO_CONTENT);
    return getById(instance.getString("id")).jsonBody();
  }

  static TestResponse getById(UUID id) {
    return getById(id.toString());
  }

  static TestResponse getById(String id) {
    return await(doGet(client, INSTANCES + "/" + id));
  }

  static void assertGetNotFound(String path) {
    assertThat(await(doGet(client, path)).status()).isEqualTo(SC_NOT_FOUND);
  }

  static void assertExists(JsonObject expectedInstance) {
    var response = getById(expectedInstance.getString("id"));
    assertThat(response.status()).isEqualTo(SC_OK);
    assertThat(response.body().toString()).contains(expectedInstance.getString("title"));
  }

  static JsonObject searchForInstances(String cql) {
    var response = await(doGet(client, INSTANCES + "?query=" + urlEncode(cql)));
    assertThat(response.status()).isEqualTo(SC_OK);
    return response.jsonBody();
  }

  static String urlEncode(String value) {
    return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  static void create5instances() {
    createInstance(smallAngryPlanet(UUID.randomUUID()));
    createInstance(nod(UUID.randomUUID()));
    createInstance(uprooted(UUID.randomUUID()));
    createInstance(temeraire(UUID.randomUUID()));
    createInstance(interestingTimes(UUID.randomUUID()));
  }

  static void setInstanceSequence(long sequenceNumber) {
    runQuery("select setval('hrid_instances_seq'," + sequenceNumber + ",FALSE)");
  }

  static void putMarcJson(UUID id, MarcJson marcJson) {
    // JsonObject.mapFrom (not pojo2JsonObject) - RMB's ObjectMapperTool has no module for Vert.x's
    // JsonObject/JsonArray, so it would serialize the nested JsonObject fields via their "map"
    // getter instead of as plain JSON objects.
    var response = await(doPut(client, INSTANCES + "/" + id + "/source-record/marc-json",
      JsonObject.mapFrom(marcJson)));
    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
  }

  static void assertMarcJsonNotFound(UUID id) {
    assertGetNotFound(INSTANCES + "/" + id + "/source-record/marc-json");
  }

  private static InstanceEventMessageChecks eventMessageChecks() {
    try {
      return new InstanceEventMessageChecks(KAFKA_CONSUMER, new URI(wm.baseUrl()).toURL());
    } catch (MalformedURLException | URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }
}
