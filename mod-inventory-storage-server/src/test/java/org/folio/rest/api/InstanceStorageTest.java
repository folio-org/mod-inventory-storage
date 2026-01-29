package org.folio.rest.api;

import static java.lang.String.format;
import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.rest.impl.StorageHelper.MAX_ENTITIES;
import static org.folio.rest.support.HttpResponseMatchers.errorMessageContains;
import static org.folio.rest.support.HttpResponseMatchers.errorParametersValueIs;
import static org.folio.rest.support.HttpResponseMatchers.statusCodeIs;
import static org.folio.rest.support.JsonArrayHelper.toList;
import static org.folio.rest.support.JsonObjectMatchers.hasSoleMessageContaining;
import static org.folio.rest.support.JsonObjectMatchers.identifierMatches;
import static org.folio.rest.support.ResponseHandler.json;
import static org.folio.rest.support.ResponseHandler.text;
import static org.folio.rest.support.http.InterfaceUrls.holdingsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.instancesStorageSyncUnsafeUrl;
import static org.folio.rest.support.http.InterfaceUrls.instancesStorageSyncUrl;
import static org.folio.rest.support.http.InterfaceUrls.instancesStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.itemsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.natureOfContentTermsUrl;
import static org.folio.rest.support.matchers.DateTimeMatchers.hasIsoFormat;
import static org.folio.rest.support.matchers.DateTimeMatchers.withinSecondsBeforeNow;
import static org.folio.rest.support.matchers.PostgresErrorMessageMatchers.isMaximumSequenceValueError;
import static org.folio.util.StringUtil.urlEncode;
import static org.folio.utility.ModuleUtility.getClient;
import static org.folio.utility.ModuleUtility.getVertx;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.folio.validator.NotesValidators.MAX_NOTE_LENGTH;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.joda.time.Seconds.seconds;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.HttpStatus;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.InstanceDates;
import org.folio.rest.jaxrs.model.InstanceNote;
import org.folio.rest.jaxrs.model.MarcJson;
import org.folio.rest.jaxrs.model.NatureOfContentTerm;
import org.folio.rest.jaxrs.model.Subject;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.IndividualResource;
import org.folio.rest.support.JsonErrorResponse;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.builders.HoldingRequestBuilder;
import org.folio.rest.support.builders.ItemRequestBuilder;
import org.folio.rest.support.db.OptimisticLocking;
import org.folio.rest.support.messages.InstanceEventMessageChecks;
import org.folio.rest.tools.utils.OptimisticLockingUtil;
import org.folio.utility.LocationUtility;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class InstanceStorageTest extends TestBaseWithInventoryUtil {
  public static final String SUBJECTS_KEY = "subjects";
  private static final String INSTANCES_KEY = "instances";
  private static final String TOTAL_RECORDS_KEY = "totalRecords";
  private static final String METADATA_KEY = "metadata";
  private static final String DATES_KEY = "dates";
  private static final String TAG_VALUE = "test-tag";
  private static final String STATUS_UPDATED_DATE_PROPERTY = "statusUpdatedDate";
  private static final Logger log = LogManager.getLogger();
  private static final String DISCOVERY_SUPPRESS = "discoverySuppress";
  private static final String STAFF_SUPPRESS = "staffSuppress";
  private static final String INVALID_VALUE = "invalid value";
  private static final String INVALID_TYPE_ERROR_MESSAGE = String.format("invalid input syntax for type uuid: \"%s\"",
    INVALID_VALUE);

  private final Set<String> natureOfContentIdsToRemoveAfterTest = new HashSet<>();

  private final InstanceEventMessageChecks instanceMessageChecks
    = new InstanceEventMessageChecks(KAFKA_CONSUMER);
  /**
   * MARC record representation in JSON, compatible with MarcEdit's JSON export and import.
   */
  private final MarcJson marcJson = new MarcJson();

  {
    marcJson.setLeader("xxxxxnam a22yyyyy c 4500");
    List<Object> fields = new ArrayList<>();
    fields.add(new JsonObject().put("001", "029857716"));
    fields.add(new JsonObject().put("245",
      new JsonObject().put("ind1", "0").put("ind2", "4").put("subfields",
        new JsonArray().add(new JsonObject().put("a", "The Yearbook of Okapiology")))));
    marcJson.setFields(fields);
  }

  public static JsonObject smallAngryPlanet(UUID id) {
    JsonArray identifiers = new JsonArray();
    identifiers.add(identifier(UUID_ISBN, "9781473619777"));
    JsonArray contributors = new JsonArray();
    contributors.add(contributor(UUID_PERSONAL_NAME, "Chambers, Becky"));
    JsonArray tags = new JsonArray();
    tags.add("test-tag");

    return createInstanceRequest(id, "MARC", "Long Way to a Small Angry Planet",
      identifiers, contributors, UUID_INSTANCE_TYPE, tags);
  }

  @SneakyThrows
  @Before
  public void beforeEach() {
    StorageTestSuite.deleteAll(itemsStorageUrl(""), TENANT_ID);
    StorageTestSuite.deleteAll(holdingsStorageUrl(""), TENANT_ID);
    StorageTestSuite.deleteAll(instancesStorageUrl(""), TENANT_ID);
    OptimisticLockingUtil.configureAllowSuppressOptimisticLocking(Map.of());
    natureOfContentIdsToRemoveAfterTest.clear();

    removeAllEvents();
  }

  @SneakyThrows
  @After
  public void afterEach(TestContext context) {
    setInstanceSequence(1);

    StorageTestSuite.checkForMismatchedIds("instance");

    // This calls get() to ensure blocking until all futures are complete.
    final Async async = context.async();
    List<CompletableFuture<Response>> cfs = new ArrayList<>();
    natureOfContentIdsToRemoveAfterTest.forEach(id -> cfs.add(getClient()
      .delete(natureOfContentTermsUrl("/" + id), TENANT_ID)));
    CompletableFuture.allOf(cfs.toArray(new CompletableFuture[0]))
      .whenComplete((v, t) -> async.complete());

    removeAllEvents();
  }

  @Test
  public void canCreateAnInstance() throws InterruptedException,
    ExecutionException, TimeoutException {

    UUID id = UUID.randomUUID();
    String[] natureOfContentIds = createNatureOfContentTerms();
    String adminNote = "Administrative note";
    JsonObject instanceToCreate = createInstanceWithAllFields(id, natureOfContentIds, adminNote);

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();
    getClient().post(instancesStorageUrl(""), instanceToCreate, TENANT_ID, json(createCompleted));
    Response response = createCompleted.get(10, SECONDS);
    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject instance = response.getJson();
    verifyCreatedInstanceFields(instance, id, natureOfContentIds, adminNote);

    JsonObject instanceFromGet = getById(id).getJson();
    verifyRetrievedInstanceFields(instanceFromGet, natureOfContentIds);

    instanceMessageChecks.createdMessagePublished(instanceFromGet);
    verifyInstanceDatesAndSubjects(instance);
  }

  @Test
  public void canCreateAnInstanceWithoutProvidingId()
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    JsonObject instanceToCreate = smallAngryPlanet(null);

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    getClient().post(instancesStorageUrl(""), instanceToCreate, TENANT_ID,
      json(createCompleted));

    Response response = createCompleted.get(10, SECONDS);

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject instance = response.getJson();

    String newId = instance.getString("id");

    assertThat(newId, is(notNullValue()));

    Response getResponse = getById(newId);

    assertThat(getResponse.getStatusCode(), is(HTTP_OK));

    JsonObject instanceFromGet = getResponse.getJson();

    assertThat(instanceFromGet.getString("title"),
      is("Long Way to a Small Angry Planet"));

    JsonArray identifiers = instanceFromGet.getJsonArray("identifiers");
    assertThat(identifiers.size(), is(1));
    assertThat(identifiers, hasItem(identifierMatches(UUID_ISBN.toString(), "9781473619777")));
    assertThat(instanceFromGet.getString(STATUS_UPDATED_DATE_PROPERTY), hasIsoFormat());
  }

  @Test
  public void cannotCreateAnInstanceWithIdThatIsNotUuid()
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    String id = "6556456";

    JsonArray identifiers = new JsonArray();
    identifiers.add(identifier(UUID_ISBN, "9781473619777"));

    JsonArray contributors = new JsonArray();
    contributors.add(contributor(UUID_PERSONAL_NAME, "Chambers, Becky"));

    JsonObject instanceToCreate = new JsonObject();

    instanceToCreate.put("id", id);
    instanceToCreate.put("source", "TEST");
    instanceToCreate.put("title", "Long Way to a Small Angry Planet");
    instanceToCreate.put("identifiers", identifiers);
    instanceToCreate.put("contributors", contributors);
    instanceToCreate.put("instanceTypeId", UUID_TEXT.toString());

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    getClient().post(instancesStorageUrl(""), instanceToCreate, TENANT_ID,
      json(createCompleted));

    Response response = createCompleted.get(10, SECONDS);

    assertThat(response.getStatusCode(), is(422));

    assertThat(response.getBody(), containsString("must match"));
  }

  @SneakyThrows
  @Test
  public void cannotCreateAnInstanceWithInvalidStatisticalCodeIds() {

    JsonObject instanceToCreate = smallAngryPlanet(null);
    instanceToCreate.put(STATISTICAL_CODE_IDS_KEY, Set.of(INVALID_VALUE));

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    getClient().post(instancesStorageUrl(""), instanceToCreate, TENANT_ID,
      text(createCompleted));

    Response response = createCompleted.get(10, SECONDS);
    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), containsString(INVALID_TYPE_ERROR_MESSAGE));
  }

  @Test
  public void cannotUpdateAnInstanceWithNotExistingSubjectIds()
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    var instanceToCreate = smallAngryPlanet(null);
    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    getClient().post(instancesStorageUrl(""), instanceToCreate, TENANT_ID,
      json(createCompleted));

    var response = createCompleted.get(10, SECONDS);
    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    var instance = response.getJson();
    var newId = instance.getString("id");

    assertThat(newId, is(notNullValue()));

    var getResponse = getById(newId);

    assertThat(getResponse.getStatusCode(), is(HTTP_OK));

    var instanceFromGet = getResponse.getJson();
    var subject = new Subject()
      .withSourceId(UUID.randomUUID().toString())
      .withValue("subject");
    var subjects = new JsonArray().add(subject);
    instanceFromGet.put(SUBJECTS_KEY, subjects);

    var updatedResponse = update(instanceFromGet);
    assertThat(updatedResponse.getStatusCode(), is(HTTP_BAD_REQUEST));
  }

  @Test
  public void cannotCreateAnInstanceWithNotExistingSubjectIds()
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    var instanceToCreate = smallAngryPlanet(null);
    var subject = new Subject()
      .withSourceId(UUID.randomUUID().toString())
      .withTypeId(UUID.randomUUID().toString())
      .withValue("subject");
    var subjects = new JsonArray().add(subject);
    instanceToCreate.put(SUBJECTS_KEY, subjects);

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    getClient().post(instancesStorageUrl(""), instanceToCreate, TENANT_ID,
      text(createCompleted));

    var response = createCompleted.get(10, SECONDS);

    assertThat(response.getStatusCode(), is(HTTP_BAD_REQUEST));
  }

  @Test
  public void canUpdateAnInstanceUnlinkSubjectSourceAndType() {

    UUID id = UUID.randomUUID();
    var subject = new Subject()
      .withSourceId(UUID_INSTANCE_SUBJECT_SOURCE_ID.toString())
      .withTypeId(UUID_INSTANCE_SUBJECT_TYPE_ID.toString())
      .withValue("subject");
    var subjects = new JsonArray().add(subject);
    JsonObject instanceToCreate = smallAngryPlanet(id);
    instanceToCreate.put(SUBJECTS_KEY, subjects);

    var newId = createInstanceRecord(instanceToCreate);

    assertThat(newId, is(notNullValue()));

    var getResponse = getById(newId);

    assertThat(getResponse.getStatusCode(), is(HTTP_OK));
    var instanceFromGet = getResponse.getJson();
    instanceFromGet.put(SUBJECTS_KEY, null);

    var updatedResponse = update(instanceFromGet);
    assertThat(updatedResponse.getStatusCode(), is(HTTP_NO_CONTENT));
  }

  @Test
  public void canUpdateAnInstanceLinkAndUnlinkSubjectSourceAndType() {

    UUID id = UUID.randomUUID();
    var subject = new Subject()
      .withSourceId(UUID_INSTANCE_SUBJECT_SOURCE_ID.toString())
      .withTypeId(UUID_INSTANCE_SUBJECT_TYPE_ID.toString())
      .withValue("subject");
    var subjects = new JsonArray().add(subject);
    JsonObject instanceToCreate = smallAngryPlanet(id);
    instanceToCreate.put(SUBJECTS_KEY, subjects);

    var newId = createInstanceRecord(instanceToCreate);

    assertThat(newId, is(notNullValue()));

    var getResponse = getById(newId);

    assertThat(getResponse.getStatusCode(), is(HTTP_OK));
    var instanceFromGet = getResponse.getJson();
    var updatedSubject = new Subject()
      .withSourceId("e894d0dc-621d-4b1d-98f6-6f7120eb0d41")
      .withTypeId("d6488f88-1e74-40ce-81b5-b19a928ff5b2")
      .withValue("subject upd");
    var updatedSubjects = new JsonArray().add(updatedSubject);
    instanceFromGet.put(SUBJECTS_KEY, updatedSubjects);

    var updatedResponse = update(instanceFromGet);
    assertThat(updatedResponse.getStatusCode(), is(HTTP_NO_CONTENT));
  }

  @Test
  public void cannotPutAnInstanceAtNonexistingLocation()
    throws InterruptedException, ExecutionException, TimeoutException {

    UUID id = UUID.randomUUID();

    JsonObject instanceToCreate = nod(id);

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    URL url = instancesStorageUrl(format("/%s", id));
    getClient().put(url, instanceToCreate,
      TENANT_ID, ResponseHandler.empty(createCompleted));

    Response putResponse = createCompleted.get(10, SECONDS);
    assertThat(putResponse.getStatusCode(), is(HTTP_NOT_FOUND));

    assertGetNotFound(url);
  }

  @Test
  public void creatingInstanceLimitNoteMaximumLength()
    throws ExecutionException, InterruptedException, TimeoutException {
    UUID id = UUID.randomUUID();
    JsonObject instanceToCreate = smallAngryPlanet(id);
    instanceToCreate.put("notes", new JsonArray().add(new InstanceNote().withNote("x".repeat(MAX_NOTE_LENGTH + 1))));

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    getClient().post(instancesStorageUrl(""), instanceToCreate,
      TENANT_ID, json(createCompleted));

    Response response = createCompleted.get(2, SECONDS);

    assertThat(response.getStatusCode(), is(422));
  }

  @Test
  public void creatingInstanceLimitAdministrativeNoteMaximumLength()
    throws ExecutionException, InterruptedException, TimeoutException {
    UUID id = UUID.randomUUID();
    JsonObject instanceToCreate = smallAngryPlanet(id);
    instanceToCreate.put("administrativeNotes", new JsonArray().add("x".repeat(MAX_NOTE_LENGTH + 1)));

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    getClient().post(instancesStorageUrl(""), instanceToCreate,
      TENANT_ID, json(createCompleted));

    Response response = createCompleted.get(2, SECONDS);

    assertThat(response.getStatusCode(), is(422));
  }

  @Test
  public void updatingInstanceLimitAdministrativeNoteMaximumLength()
    throws ExecutionException, InterruptedException, TimeoutException {
    UUID id = UUID.randomUUID();
    createInstance(smallAngryPlanet(id));
    JsonObject instance = getById(id).getJson();
    instance.put("administrativeNotes", new JsonArray().add("x".repeat(MAX_NOTE_LENGTH + 1)));
    assertThat(update(instance).getStatusCode(), is(422));
  }

  @Test
  public void updatingInstanceLimitNoteMaximumLength()
    throws ExecutionException, InterruptedException, TimeoutException {
    UUID id = UUID.randomUUID();
    createInstance(smallAngryPlanet(id));
    JsonObject instance = getById(id).getJson();
    instance.put("notes", new JsonArray().add(new InstanceNote().withNote("x".repeat(MAX_NOTE_LENGTH + 1))));
    assertThat(update(instance).getStatusCode(), is(422));
  }

  @Test
  public void optimisticLockingVersion() throws Exception {
    UUID id = UUID.randomUUID();
    createInstance(nod(id));
    JsonObject instance = getById(id).getJson();
    instance.put("title", "foo");
    // updating with current _version 1 succeeds and increments _version to 2
    assertThat(update(instance).getStatusCode(), is(204));
    instance.put("title", "bar");
    // updating with outdated _version 1 fails, current _version is 2
    int expected = OptimisticLocking.hasFailOnConflict("instance") ? 409 : 204;
    assertThat(update(instance).getStatusCode(), is(expected));
    // updating with _version -1 should fail, single instance PUT never allows to suppress optimistic locking
    instance.put("_version", -1);
    assertThat(update(instance).getStatusCode(), is(409));
    // this allow should not apply to single instance PUT, only to batch unsafe
    OptimisticLockingUtil.configureAllowSuppressOptimisticLocking(
      Map.of(OptimisticLockingUtil.DB_ALLOW_SUPPRESS_OPTIMISTIC_LOCKING, "9999-12-31T23:59:59Z"));
    instance.put("_version", -1);
    assertThat(update(instance).getStatusCode(), is(409));
  }

  @Test
  @SneakyThrows
  public void shouldNotUpdateInstanceIfNoChanges() {
    var id = UUID.randomUUID();
    createInstance(nod(id));
    var instance = getById(id).getJson();
    instanceMessageChecks.createdMessagePublished(id.toString());

    assertThat(update(instance).getStatusCode(), is(204));

    var updatedInstance = getById(id).getJson();
    //assert that there was no update in database
    assertThat(updatedInstance.getString("_version"), is("1"));
    var kafkaEvents = KAFKA_CONSUMER.getMessagesForInstance(id.toString());
    //assert that there's only CREATE kafka message, no updates
    assertThat(kafkaEvents.size(), is(1));
  }

  @Test
  public void cannotProvideAdditionalPropertiesInInstance()
    throws InterruptedException, TimeoutException, ExecutionException {

    JsonObject requestWithAdditionalProperty = nod(UUID.randomUUID());

    requestWithAdditionalProperty.put("somethingAdditional", "foo");

    CompletableFuture<JsonErrorResponse> createCompleted = new CompletableFuture<>();

    getClient().post(instancesStorageUrl(""), requestWithAdditionalProperty,
      TENANT_ID, ResponseHandler.jsonErrors(createCompleted));

    JsonErrorResponse response = createCompleted.get(10, SECONDS);

    assertThat(response.getStatusCode(), is(HttpStatus.HTTP_UNPROCESSABLE_ENTITY.toInt()));
    assertThat(response.getErrors(), hasSoleMessageContaining("Unrecognized field"));
  }

  @Test
  public void cannotProvideAdditionalPropertiesInInstanceIdentifiers()
    throws InterruptedException, TimeoutException, ExecutionException {

    JsonObject requestWithAdditionalProperty = nod(UUID.randomUUID());

    requestWithAdditionalProperty
      .getJsonArray("identifiers").add(identifier(UUID_ISBN, "5645678432576")
        .put("somethingAdditional", "foo"));

    CompletableFuture<JsonErrorResponse> createCompleted = new CompletableFuture<>();

    getClient().post(instancesStorageUrl(""), requestWithAdditionalProperty,
      TENANT_ID, ResponseHandler.jsonErrors(createCompleted));

    JsonErrorResponse response = createCompleted.get(10, SECONDS);

    assertThat(response.getStatusCode(), is(HttpStatus.HTTP_UNPROCESSABLE_ENTITY.toInt()));
    assertThat(response.getErrors(), hasSoleMessageContaining("Unrecognized field"));
  }

  @Test
  public void canReplaceAnInstanceAtSpecificLocation() throws InterruptedException,
    ExecutionException, TimeoutException {

    UUID id = UUID.randomUUID();
    String adminNote = "An Admin note";
    final IndividualResource createdInstance = createInstance(smallAngryPlanet(id));

    // Clear Kafka events after create to reduce chances of
    // CREATE messages appearing after UPDATE later on.
    // This should be removed once the messaging problem is
    // properly resolved.
    removeAllEvents();

    JsonObject replacement = createdInstance.copyJson();
    replacement.put("title", "A Long Way to a Small Angry Planet");
    replacement.put("administrativeNotes", new JsonArray().add(adminNote));

    CompletableFuture<Response> replaceCompleted = new CompletableFuture<>();

    getClient().put(instancesStorageUrl(format("/%s", id)), replacement,
      TENANT_ID, ResponseHandler.empty(replaceCompleted));

    Response putResponse = replaceCompleted.get(10, SECONDS);

    //PUT currently cannot return a response
    assertThat(putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    Response updatedInstance = getById(id);

    assertThat(updatedInstance.getStatusCode(), is(HTTP_OK));

    JsonObject itemFromGet = updatedInstance.getJson();

    assertThat(itemFromGet.getString("id"), is(id.toString()));
    assertThat(itemFromGet.getString("title"), is("A Long Way to a Small Angry Planet"));
    assertThat(itemFromGet.getString(STATUS_UPDATED_DATE_PROPERTY),
      is(replacement.getString(STATUS_UPDATED_DATE_PROPERTY)));
    assertThat(itemFromGet.getBoolean(DISCOVERY_SUPPRESS), is(false));
    assertThat(itemFromGet.getJsonArray("administrativeNotes").contains(adminNote), is(true));

    instanceMessageChecks.updatedMessagePublished(createdInstance.getJson(),
      updatedInstance.getJson());
  }

  @Test
  @SneakyThrows
  public void canDeleteAnInstance() {
    UUID id = UUID.randomUUID();

    JsonObject instanceToCreate = smallAngryPlanet(id);

    final IndividualResource createdInstance = createInstance(instanceToCreate);

    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();

    URL url = instancesStorageUrl(format("/%s", id));
    getClient().delete(url, TENANT_ID, ResponseHandler.empty(deleteCompleted));

    Response deleteResponse = deleteCompleted.get(10, SECONDS);

    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    assertGetNotFound(url);

    instanceMessageChecks.deletedMessagePublished(createdInstance.getJson());
  }

  @SneakyThrows
  @Test
  public void cannotDeleteInstanceThatDoesNotExist() {

    var response = getClient().delete(instancesStorageUrl("/" + UUID.randomUUID()), TENANT_ID).get(10, SECONDS);

    assertThat(response.getStatusCode(), is(404));
  }

  @SneakyThrows
  @Test
  public void canDeleteInstancesByCql() {

    final var id5 = UUID.randomUUID();
    final var instance1 = createInstance(nod(UUID.randomUUID()).put("hrid", "1234")).getJson();
    final var instance2 = createInstance(nod(UUID.randomUUID()).put("hrid", "2123")).getJson();
    final var instance3 = createInstance(nod(UUID.randomUUID()).put("hrid", "12")).getJson();
    final var instance4 = createInstance(nod(UUID.randomUUID()).put("hrid", "345 12")).getJson();
    createInstance(nod(id5).put("hrid", "123")).getJson();
    put(id5, marcJson);
    final var instance5 = getById(id5).getJson();

    var response = getClient().delete(instancesStorageUrl("?query=hrid==12*"), TENANT_ID).get(10, SECONDS);

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
    assertExists(instance2);
    assertExists(instance4);
    assertNotExists(instance1);
    assertNotExists(instance3);
    assertNotExists(instance5);
    getMarcJsonNotFound(id5);

    instanceMessageChecks.deletedMessagePublished(instance1);
    instanceMessageChecks.deletedMessagePublished(instance3);
    instanceMessageChecks.deletedMessagePublished(instance5);
  }

  @SneakyThrows
  @Test
  public void cannotDeleteInstancesWithEmptyCql() {

    var response = getClient().delete(instancesStorageUrl("?query="), TENANT_ID).get(10, SECONDS);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), containsString("empty"));
  }

  @Test
  public void canGetInstanceById() throws InterruptedException, ExecutionException, TimeoutException {

    UUID id = UUID.randomUUID();

    createInstance(smallAngryPlanet(id));

    URL getInstanceUrl = instancesStorageUrl(format("/%s", id));

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    getClient().get(getInstanceUrl, TENANT_ID,
      json(getCompleted));

    Response response = getCompleted.get(10, SECONDS);

    JsonObject instance = response.getJson();

    assertThat(response.getStatusCode(), is(HTTP_OK));

    assertThat(instance.getString("id"), is(id.toString()));
    assertThat(instance.getString("title"), is("Long Way to a Small Angry Planet"));

    JsonArray identifiers = instance.getJsonArray("identifiers");
    assertThat(identifiers.size(), is(1));
    assertThat(identifiers, hasItem(identifierMatches(UUID_ISBN.toString(), "9781473619777")));
  }

  @Test
  public void canGetAllInstances() throws InterruptedException, ExecutionException, TimeoutException {
    UUID firstInstanceId = UUID.randomUUID();
    UUID secondInstanceId = UUID.randomUUID();
    createInstance(smallAngryPlanet(firstInstanceId));
    createInstance(nod(secondInstanceId));

    JsonObject responseBody = getAllInstancesResponse();
    JsonArray allInstances = responseBody.getJsonArray(INSTANCES_KEY);

    assertThat(allInstances.size(), is(2));
    assertThat(responseBody.getInteger(TOTAL_RECORDS_KEY), is(2));

    JsonObject[] instances = orderInstancesById(allInstances, firstInstanceId);
    assertSmallAngryPlanetInstance(instances[0], firstInstanceId);
    assertNodInstance(instances[1], secondInstanceId);
  }

  private JsonObject getAllInstancesResponse() throws InterruptedException, ExecutionException, TimeoutException {
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    getClient().get(instancesStorageUrl(""), TENANT_ID, json(getCompleted));
    return getCompleted.get(10, SECONDS).getJson();
  }

  private JsonObject[] orderInstancesById(JsonArray allInstances, UUID expectedFirstId) {
    JsonObject firstInstance = allInstances.getJsonObject(0);
    JsonObject secondInstance = allInstances.getJsonObject(1);
    // no "sortBy" used so the database can return them in any order. swap if needed:
    if (expectedFirstId.toString().equals(secondInstance.getString("id"))) {
      return new JsonObject[]{secondInstance, firstInstance};
    }
    return new JsonObject[]{firstInstance, secondInstance};
  }

  private void assertSmallAngryPlanetInstance(JsonObject instance, UUID expectedId) {
    assertThat(instance.getString("id"), is(expectedId.toString()));
    assertThat(instance.getString("title"), is("Long Way to a Small Angry Planet"));
    assertThat(instance.getJsonArray("identifiers").size(), is(1));
    assertThat(instance.getJsonArray("identifiers"),
      hasItem(identifierMatches(UUID_ISBN.toString(), "9781473619777")));
  }

  private void assertNodInstance(JsonObject instance, UUID expectedId) {
    assertThat(instance.getString("id"), is(expectedId.toString()));
    assertThat(instance.getString("title"), is("Nod"));
    assertThat(instance.getJsonArray("identifiers").size(), is(1));
    assertThat(instance.getJsonArray("identifiers"),
      hasItem(identifierMatches(UUID_ASIN.toString(), "B01D1PLMDO")));
  }

  @Test
  public void canRetrieveAllInstances() throws InterruptedException, ExecutionException, TimeoutException {
    var firstInstanceId = UUID.randomUUID();
    var secondInstanceId = UUID.randomUUID();
    createInstance(smallAngryPlanet(firstInstanceId));
    createInstance(nod(secondInstanceId));

    var retrieveResponses = retrieveInstancesWithAndWithoutSort();
    var allInstances = retrieveResponses[0].getJsonArray(INSTANCES_KEY);
    var sortedInstances = retrieveResponses[1].getJsonArray(INSTANCES_KEY);

    assertThat(allInstances.size(), is(2));
    assertThat(sortedInstances.size(), is(2));
    assertThat(retrieveResponses[0].getInteger(TOTAL_RECORDS_KEY), is(2));

    var orderedInstances = orderInstancesById(allInstances, firstInstanceId);
    assertSmallAngryPlanetInstance(orderedInstances[0], firstInstanceId);
    assertNodInstance(orderedInstances[1], secondInstanceId);
    assertThat(sortedInstances.getJsonObject(0).getString("title"), is("Long Way to a Small Angry Planet"));
  }

  private JsonObject[] retrieveInstancesWithAndWithoutSort()
      throws InterruptedException, ExecutionException, TimeoutException {
    var retrieveCompleted = new CompletableFuture<Response>();
    var retrieveByTitleCompleted = new CompletableFuture<Response>();

    getClient().post(instancesStorageUrl("/retrieve"), new JsonObject(), TENANT_ID, json(retrieveCompleted));
    getClient().post(instancesStorageUrl("/retrieve"),
      new JsonObject().put("query", "(cql.allRecords=1) sortBy title"), TENANT_ID, json(retrieveByTitleCompleted));

    return new JsonObject[]{
      retrieveCompleted.get(10, SECONDS).getJson(),
      retrieveByTitleCompleted.get(10, SECONDS).getJson()
    };
  }

  @Test
  public void canSearchByClassificationNumberWithoutArrayModifier()
    throws InterruptedException, ExecutionException, TimeoutException {

    createInstancesWithClassificationNumbers();

    JsonObject allInstances = searchForInstances("classifications =\"K1 .M385\"");

    assertThat(allInstances.getInteger("totalRecords"), is(1));
    assertThat(allInstances.getJsonArray("instances").getJsonObject(0).getString("title"),
      is("Long Way to a Small Angry Planet"));
  }

  @Test
  public void canSearchUsingMetadataDateUpdatedIndex()
    throws InterruptedException, ExecutionException, TimeoutException {

    UUID firstInstanceId = UUID.randomUUID();

    JsonObject firstInstanceToCreate = smallAngryPlanet(firstInstanceId);

    createInstance(firstInstanceToCreate);

    UUID secondInstanceId = UUID.randomUUID();

    JsonObject secondInstanceToCreate = nod(secondInstanceId);
    // wait 2 seconds before creating the second instance to have a different "updatedDate" field
    // than the first instance.
    await().pollDelay(2, SECONDS).until(() -> true);

    IndividualResource ir = createInstance(secondInstanceToCreate);

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    JsonObject metadata = ir.getJson().getJsonObject("metadata");

    String query = urlEncode(format("%s.updatedDate>=%s", METADATA_KEY, metadata.getString("updatedDate")));

    getClient().get(instancesStorageUrl(format("?query=%s", query)), TENANT_ID,
      ResponseHandler.json(getCompleted));

    Response response = getCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    JsonObject responseBody = response.getJson();

    JsonArray allInstances = responseBody.getJsonArray("instances");

    assertThat(allInstances.size(), is(1));

    JsonObject instance = allInstances.getJsonObject(0);

    assertThat(instance.getString("title"), is(ir.getJson().getString("title")));
  }

  @Test
  public void canPageAllInstances() throws InterruptedException, ExecutionException, TimeoutException {

    createInstance(smallAngryPlanet(UUID.randomUUID()));
    createInstance(nod(UUID.randomUUID()));
    createInstance(uprooted(UUID.randomUUID()));
    createInstance(temeraire(UUID.randomUUID()));
    createInstance(interestingTimes(UUID.randomUUID()));

    CompletableFuture<Response> firstPageCompleted = new CompletableFuture<>();
    CompletableFuture<Response> secondPageCompleted = new CompletableFuture<>();

    getClient().get(instancesStorageUrl("") + "?limit=3", TENANT_ID,
      json(firstPageCompleted));

    getClient().get(instancesStorageUrl("") + "?limit=3&offset=3", TENANT_ID,
      json(secondPageCompleted));

    Response firstPageResponse = firstPageCompleted.get(10, SECONDS);
    Response secondPageResponse = secondPageCompleted.get(10, SECONDS);

    assertThat(firstPageResponse.getStatusCode(), is(200));
    assertThat(secondPageResponse.getStatusCode(), is(200));

    JsonObject firstPage = firstPageResponse.getJson();
    JsonObject secondPage = secondPageResponse.getJson();

    JsonArray firstPageInstances = firstPage.getJsonArray(INSTANCES_KEY);
    JsonArray secondPageInstances = secondPage.getJsonArray(INSTANCES_KEY);

    assertThat(firstPageInstances.size(), is(3));
    assertThat(firstPage.getInteger(TOTAL_RECORDS_KEY), is(5));

    assertThat(secondPageInstances.size(), is(2));
    assertThat(secondPage.getInteger(TOTAL_RECORDS_KEY), is(5));
  }

  @Test
  public void canProvideLargePageOffsetAndLimit()
    throws InterruptedException, ExecutionException, TimeoutException {

    createInstance(smallAngryPlanet(UUID.randomUUID()));
    createInstance(nod(UUID.randomUUID()));
    createInstance(uprooted(UUID.randomUUID()));
    createInstance(temeraire(UUID.randomUUID()));
    createInstance(interestingTimes(UUID.randomUUID()));

    CompletableFuture<Response> pageCompleted = new CompletableFuture<>();

    getClient().get(instancesStorageUrl("") + "?limit=5000&offset=5000", TENANT_ID,
      json(pageCompleted));

    Response pageResponse = pageCompleted.get(10, SECONDS);

    assertThat(pageResponse.getStatusCode(), is(200));

    JsonObject page = pageResponse.getJson();

    JsonArray instances = page.getJsonArray(INSTANCES_KEY);

    assertThat(instances.size(), is(0));
    // Reports 0, not sure if this is to due with record count approximation
    // "assertThat(page.getInteger(TOTAL_RECORDS_KEY), is(5));"
  }

  @Test
  public void canGetWithOptimizedSql(TestContext testContext) {
    var n = PgUtil.getOptimizedSqlSize() / 2;
    var pg = PostgresClient.getInstance(getVertx(), TENANT_ID);
    insertTestDataForOptimizedSql(testContext, pg, n);

    assertOptimizedSqlLimit9Results();
    assertOptimizedSqlLimit5Results();
    assertOptimizedSqlOffset6Limit3Results();
    assertOptimizedSqlOffset1Limit8Results();
    assertOptimizedSqlOffset1Limit20Results();
    assertOptimizedSqlDescendingOffset1Limit3Results();
    assertOptimizedSqlDescendingOffset6Limit3Results();
  }

  private void insertTestDataForOptimizedSql(TestContext testContext, PostgresClient pg, int n) {
    // "b foo" records are before the getOptimizedSqlSize() limit
    // "d foo" records are after the getOptimizedSqlSize() limit
    insert(testContext, pg, "a", n);
    insert(testContext, pg, "b foo", 5);
    insert(testContext, pg, "c", n);
    insert(testContext, pg, "d foo", 5);
    insert(testContext, pg, "e", n);
  }

  private void assertOptimizedSqlLimit9Results() {
    var json = searchForInstances("title=foo sortBy title", 0, 9);
    var allInstances = json.getJsonArray(INSTANCES_KEY);
    assertThat(allInstances.size(), is(9));
    assertThat(json.getInteger(TOTAL_RECORDS_KEY), is(10));
    assertInstanceTitles(allInstances, 0, 5, "b foo ");
    assertInstanceTitles(allInstances, 5, 3, "d foo ");
  }

  private void assertOptimizedSqlLimit5Results() {
    var json = searchForInstances("title=foo sortBy title", 0, 5);
    var allInstances = json.getJsonArray(INSTANCES_KEY);
    assertThat(allInstances.size(), is(5));
    assertThat(json.getInteger(TOTAL_RECORDS_KEY), is(10));
    assertInstanceTitles(allInstances, 0, 5, "b foo ");
  }

  private void assertOptimizedSqlOffset6Limit3Results() {
    var json = searchForInstances("title=foo sortBy title", 6, 3);
    var allInstances = json.getJsonArray(INSTANCES_KEY);
    assertThat(allInstances.size(), is(3));
    assertThat(json.getInteger(TOTAL_RECORDS_KEY), is(10));
    for (int i = 0; i < 3; i++) {
      assertThat(allInstances.getJsonObject(i).getString("title"), is("d foo " + (2 + i)));
    }
  }

  private void assertOptimizedSqlOffset1Limit8Results() {
    var json = searchForInstances("title=foo sortBy title", 1, 8);
    var allInstances = json.getJsonArray(INSTANCES_KEY);
    assertThat(allInstances.size(), is(8));
    assertThat(json.getInteger(TOTAL_RECORDS_KEY), is(10));
    for (int i = 0; i < 4; i++) {
      assertThat(allInstances.getJsonObject(i).getString("title"), is("b foo " + (2 + i)));
    }
    assertInstanceTitles(allInstances, 4, 4, "d foo ");
  }

  private void assertOptimizedSqlOffset1Limit20Results() {
    var json = searchForInstances("title=b sortBy title/sort.ascending", 1, 20);
    var allInstances = json.getJsonArray(INSTANCES_KEY);
    assertThat(allInstances.size(), is(4));
    assertThat(json.getInteger(TOTAL_RECORDS_KEY), is(5));
    for (int i = 0; i < 4; i++) {
      assertThat(allInstances.getJsonObject(i).getString("title"), is("b foo " + (2 + i)));
    }
  }

  private void assertOptimizedSqlDescendingOffset1Limit3Results() {
    var json = searchForInstances("title=foo sortBy title/sort.descending", 1, 3);
    var allInstances = json.getJsonArray(INSTANCES_KEY);
    assertThat(allInstances.size(), is(3));
    assertThat(json.getInteger(TOTAL_RECORDS_KEY), is(10));
    for (int i = 0; i < 3; i++) {
      assertThat(allInstances.getJsonObject(i).getString("title"), is("d foo " + (4 - i)));
    }
  }

  private void assertOptimizedSqlDescendingOffset6Limit3Results() {
    var json = searchForInstances("title=foo sortBy title/sort.descending", 6, 3);
    var allInstances = json.getJsonArray(INSTANCES_KEY);
    assertThat(allInstances.size(), is(3));
    assertThat(json.getInteger(TOTAL_RECORDS_KEY), is(10));
    for (int i = 0; i < 3; i++) {
      assertThat(allInstances.getJsonObject(i).getString("title"), is("b foo " + (4 - i)));
    }
  }

  private void assertInstanceTitles(JsonArray instances, int startIndex, int count, String prefix) {
    for (int i = 0; i < count; i++) {
      assertThat(instances.getJsonObject(startIndex + i).getString("title"), is(prefix + (i + 1)));
    }
  }

  @Test
  public void canCreateInstanceSourceRecord() throws Exception {
    UUID id = UUID.randomUUID();
    JsonObject instance = smallAngryPlanet(id);
    createInstance(instance);

    put(id, marcJson);

    Response getResponse = getMarcJson(id);
    assertThat(getResponse.getStatusCode(), is(200));
    assertThat(getResponse.getJson().getString("id"), is(id.toString()));
    assertThat(getResponse.getJson().getString("leader"), is("xxxxxnam a22yyyyy c 4500"));
    JsonArray fields = getResponse.getJson().getJsonArray("fields");
    assertThat(fields.getJsonObject(0).getString("001"), is("029857716"));
    assertThat(fields.getJsonObject(1).getJsonObject("245")
      .getJsonArray("subfields").getJsonObject(0).getString("a"), is("The Yearbook of Okapiology"));
    assertThat(getResponse.getJson().getMap().keySet(), containsInAnyOrder("id", "leader", "fields"));
    assertThat(getResponse.getJson().getString(STATUS_UPDATED_DATE_PROPERTY), nullValue());
  }

  @Test  // https://issues.folio.org/browse/MODINVSTOR-142?focusedCommentId=33665#comment-33665
  public void canCreateInstanceSourceRecord101073931X() throws Exception {
    UUID id = UUID.randomUUID();
    JsonObject instance = smallAngryPlanet(id);
    createInstance(instance);

    put(id, toMarcJson("/101073931X.mrcjson"));

    Response getResponse = getMarcJson(id);
    assertThat(getResponse.getStatusCode(), is(200));
    JsonArray fields = getResponse.getJson().getJsonArray("fields");
    assertThat(fields.getJsonObject(0).getString("001"), is("101073931X"));
    assertThat(getResponse.getJson().getString(STATUS_UPDATED_DATE_PROPERTY), nullValue());
  }

  @Test  // https://issues.folio.org/browse/MODINVSTOR-143?focusedCommentId=33618#comment-33618
  public void canCreateInstanceSourceRecord1011273942() throws Exception {
    UUID id = UUID.randomUUID();
    JsonObject instance = smallAngryPlanet(id);
    createInstance(instance);

    put(id, toMarcJson("/1011273942.mrcjson"));

    Response getResponse = getMarcJson(id);
    assertThat(getResponse.getStatusCode(), is(200));
    JsonArray fields = getResponse.getJson().getJsonArray("fields");
    assertThat(fields.getJsonObject(0).getString("001"), is("1011273942"));
    assertThat(getResponse.getJson().getString(STATUS_UPDATED_DATE_PROPERTY), nullValue());
  }

  @Test
  public void canUpdateInstanceSourceRecord() throws Exception {
    UUID id = UUID.randomUUID();
    JsonObject instance = smallAngryPlanet(id);
    createInstance(instance);

    put(id, marcJson);                          // create
    put(id, toMarcJson("/101073931X.mrcjson")); // update

    Response getResponse = getMarcJson(id);
    assertThat(getResponse.getStatusCode(), is(200));
    JsonArray fields = getResponse.getJson().getJsonArray("fields");
    assertThat(fields.getJsonObject(0).getString("001"), is("101073931X"));
    assertThat(getResponse.getJson().getString(STATUS_UPDATED_DATE_PROPERTY), nullValue());
  }

  @Test
  public void cannotGetNonExistingSourceRecord() {
    getMarcJsonNotFound(UUID.randomUUID());
  }

  @Test
  public void canDeleteInstanceMarcSourceRecord() throws Exception {
    UUID id = UUID.randomUUID();
    JsonObject instance = smallAngryPlanet(id);
    createInstance(instance);
    assertThat(getSourceRecordFormat(id), is(nullValue()));

    put(id, marcJson);
    assertThat(getSourceRecordFormat(id), is("MARC-JSON"));

    // delete MARC source record
    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();
    getClient().delete(instancesStorageUrl("/" + id + "/source-record/marc-json"),
      TENANT_ID, ResponseHandler.empty(deleteCompleted));
    Response deleteResponse = deleteCompleted.get(10, SECONDS);
    assertThat(deleteResponse.getStatusCode(), is(HttpStatus.HTTP_NO_CONTENT.toInt()));
    assertThat(getSourceRecordFormat(id), is(nullValue()));
    getMarcJsonNotFound(id);
  }

  @Test
  public void canDeleteInstanceSourceRecord() throws Exception {
    UUID id = UUID.randomUUID();
    JsonObject instance = smallAngryPlanet(id);
    createInstance(instance);
    assertThat(getSourceRecordFormat(id), is(nullValue()));

    put(id, marcJson);
    assertThat(getSourceRecordFormat(id), is("MARC-JSON"));

    // delete source record
    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();
    getClient().delete(instancesStorageUrl("/" + id + "/source-record"),
      TENANT_ID, ResponseHandler.empty(deleteCompleted));
    Response deleteResponse = deleteCompleted.get(10, SECONDS);
    assertThat(deleteResponse.getStatusCode(), is(HttpStatus.HTTP_NO_CONTENT.toInt()));
    assertThat(getSourceRecordFormat(id), is(nullValue()));
    getMarcJsonNotFound(id);
  }

  @Test
  public void canDeleteSourceRecordWhenDeletingInstance() throws Exception {
    UUID id = UUID.randomUUID();
    JsonObject instance = smallAngryPlanet(id);
    createInstance(instance);

    put(id, marcJson);

    // delete instance
    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();
    getClient().delete(instancesStorageUrl("/" + id),
      TENANT_ID, ResponseHandler.empty(deleteCompleted));
    Response deleteResponse = deleteCompleted.get(10, SECONDS);
    assertThat(deleteResponse.getStatusCode(), is(HttpStatus.HTTP_NO_CONTENT.toInt()));

    getMarcJsonNotFound(id);
  }

  @Test
  public void cannotCreateSourceRecordWithoutInstance() throws Exception {
    put(UUID.randomUUID(), marcJson, HttpStatus.HTTP_NOT_FOUND);
  }

  @Test
  public void canSearchForInstancesByTitle() {
    canSort("title=\"Upr*\"", "Uprooted");
    // Note that 'Up' is a stop word, and will be removed from the query!
    // We have an issue for dropping stop words: RMB-228
    // Until then, search for "upr*" instead of "up*"
  }

  @Test
  public void canSearchForInstancesByTitleWord() {
    canSort("title=\"Times\"", "Interesting Times");
  }

  @Test
  public void canSearchForInstancesByTitleAdj() {
    canSort("title adj \"Upro*\"", "Uprooted");
  }

  @Test
  public void canSearchForInstancesUsingSimilarQueryToUiLookAheadSearch() {
    canSort("title=\"upr*\" or contributors=\"name\": \"upr*\" or identifiers=\"value\": \"upr*\"", "Uprooted");
  }

  @Test
  public void arrayModifierfsIdentifiers1() {
    canSort("identifiers = /@value 9781447294146", "Uprooted");
  }

  @Test
  public void arrayModifierfsIdentifiers2() {
    canSort("identifiers = /@identifierTypeId = " + UUID_ISBN + " 9781447294146", "Uprooted");
  }

  @Test
  public void arrayModifierfsIdentifiers3() {
    canSort("identifiers = /@identifierTypeId " + UUID_ASIN, "Nod");
  }

  @Test
  public void canSearchWithoutSqlInjection() {
    create5instances();

    // check for MODINVSTOR-293:
    // CQL identifiers=")" fails with "invalid regular expression: parentheses () not balanced" SQL Injection
    String[] strings = {"'", "''",
                        "\\\"", "\\\"\\\"",
                        "(", "((", ")", "))",
                        "{", "{{", "}", "}}"};

    for (String s : strings) {
      // full text search ignores punctuation
      matchInstanceTitles(searchForInstances("title=\"" + s + "Uprooted\""), "Uprooted");
      // == will return 0 results
      matchInstanceTitles(searchForInstances("title==\"" + s + "Uprooted\""));
      // identifier search will always return 0 results
      matchInstanceTitles(searchForInstances("identifiers=\"" + s + "\""));
      matchInstanceTitles(searchForInstances("identifiers==\"" + s + "\""));
    }
  }

  @Test
  public void canSearchBySubjects() throws Exception {
    JsonObject first = buildInstanceWithSubjects("first", "foo", "bar", "baz");
    JsonObject second = buildInstanceWithSubjects("second", "abc def ghi", "uvw xyz");

    createInstancesWithTypeAndSource(first, second);
    assertSubjectSearchResults();
  }

  private JsonObject buildInstanceWithSubjects(String title, String... subjectValues) {
    JsonObject instance = new JsonObject().put("title", title);
    JsonArray subjects = new JsonArray();
    for (String value : subjectValues) {
      subjects.add(new JsonObject().put("value", value));
    }
    return instance.put("subjects", subjects);
  }

  private void createInstancesWithTypeAndSource(JsonObject... instances) throws Exception {
    for (JsonObject instance : instances) {
      instance.put("source", "test");
      instance.put("instanceTypeId", UUID_INSTANCE_TYPE.toString());
      createInstance(instance);
    }
  }

  private void assertSubjectSearchResults() {
    matchInstanceTitles(searchForInstances("subjects=foo"), "first");
    matchInstanceTitles(searchForInstances("subjects=bar"), "first");
    matchInstanceTitles(searchForInstances("subjects=baz"), "first");
    matchInstanceTitles(searchForInstances("subjects=abc"), "second");
    matchInstanceTitles(searchForInstances("subjects=def"), "second");
    matchInstanceTitles(searchForInstances("subjects=ghi"), "second");
    matchInstanceTitles(searchForInstances("subjects=uvw"), "second");
    matchInstanceTitles(searchForInstances("subjects=xyz"), "second");
    // phrase search
    matchInstanceTitles(searchForInstances("subjects=\"def ghi\""), "second");
    matchInstanceTitles(searchForInstances("subjects=\"uvw xyz\""), "second");
    matchInstanceTitles(searchForInstances("subjects=\"baz bar\""));
    matchInstanceTitles(searchForInstances("subjects=\"abc xyz\""));
  }

  @Test
  public void canSearchByBarcode() throws Exception {
    UUID expectedInstanceId = UUID.randomUUID();
    UUID expectedHoldingId = UUID.randomUUID();
    createInstance(smallAngryPlanet(expectedInstanceId));
    createHoldingsForInstance(expectedHoldingId, expectedInstanceId);
    createItemWithBarcode(expectedHoldingId, "706949453641");

    UUID otherInstanceId = UUID.randomUUID();
    UUID otherHoldingId = UUID.randomUUID();
    createInstance(nod(otherInstanceId));
    createHoldingsForInstance(otherHoldingId, otherInstanceId);
    createItemWithBarcode(expectedHoldingId, "766043059304");

    //Use == as exact match is intended for barcode
    canSort("item.barcode==706949453641", "Long Way to a Small Angry Planet");
  }

  private void createHoldingsForInstance(UUID holdingId, UUID instanceId) throws Exception {
    createHoldings(new HoldingRequestBuilder()
      .withId(holdingId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID)
      .forInstance(instanceId)
      .create());
  }

  private void createItemWithBarcode(UUID holdingId, String barcode) {
    createItem(new ItemRequestBuilder()
      .forHolding(holdingId)
      .withBarcode(barcode)
      .withPermanentLoanType(canCirculateLoanTypeId)
      .withMaterialType(bookMaterialTypeId)
      .create());
  }

  // This is intended to demonstrate usage of the two different views
  @Test
  public void canSearchByBarcodeAndPermanentLocation() throws Exception {
    UUID smallAngryPlanetInstanceId = UUID.randomUUID();
    UUID mainLibrarySmallAngryHoldingId = UUID.randomUUID();
    UUID annexSmallAngryHoldingId = UUID.randomUUID();
    createInstance(smallAngryPlanet(smallAngryPlanetInstanceId));
    createHoldingsWithLocationAndItem(mainLibrarySmallAngryHoldingId, smallAngryPlanetInstanceId,
      MAIN_LIBRARY_LOCATION_ID, "706949453641");
    createHoldingsWithLocationAndItem(annexSmallAngryHoldingId, smallAngryPlanetInstanceId,
      ANNEX_LIBRARY_LOCATION_ID, "70704539201");

    UUID nodInstanceId = UUID.randomUUID();
    UUID nodHoldingId = UUID.randomUUID();
    createInstance(nod(nodInstanceId));
    createHoldingsWithLocationAndItem(nodHoldingId, nodInstanceId, MAIN_LIBRARY_LOCATION_ID, "766043059304");

    assertBarcodeAndLocationSearchResults();
  }

  private void createHoldingsWithLocationAndItem(UUID holdingId, UUID instanceId,
                                                   UUID locationId, String barcode) throws Exception {
    createHoldings(new HoldingRequestBuilder()
      .withId(holdingId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(locationId)
      .forInstance(instanceId)
      .create());
    createItemWithBarcode(holdingId, barcode);
  }

  private void assertBarcodeAndLocationSearchResults() {
    //Use == as exact match is intended for barcode and location ID
    canSort(format("item.barcode==706949453641 and holdingsRecords.permanentLocationId==%s", MAIN_LIBRARY_LOCATION_ID),
      "Long Way to a Small Angry Planet");
    canSort(format("holdingsRecords.permanentLocationId=\"%s\" sortBy title/sort.descending", MAIN_LIBRARY_LOCATION_ID),
      "Nod", "Long Way to a Small Angry Planet");
  }

  // This is intended to demonstrate that instances without holdings or items
  // are not excluded from searching
  @Test
  public void canSearchByTitleAndBarcodeWithMissingHoldingsAndItemsAndStillGetInstances() throws Exception {
    UUID smallAngryPlanetInstanceId = UUID.randomUUID();
    UUID mainLibrarySmallAngryHoldingId = UUID.randomUUID();
    UUID nodInstanceId = UUID.randomUUID();

    createInstanceWithHoldingAndItem(smallAngryPlanetInstanceId, mainLibrarySmallAngryHoldingId);
    createInstance(nod(nodInstanceId));

    Response searchResponse = searchByBarcodeOrTitle();
    assertInstancesFoundInSearch(searchResponse, smallAngryPlanetInstanceId, nodInstanceId);
  }

  private void createInstanceWithHoldingAndItem(UUID instanceId, UUID holdingId) throws Exception {
    createInstance(smallAngryPlanet(instanceId));
    createHoldingsForInstance(holdingId, instanceId);
    createItemWithBarcode(holdingId, "706949453641");
  }

  private Response searchByBarcodeOrTitle() throws InterruptedException, ExecutionException, TimeoutException {
    CompletableFuture<Response> searchCompleted = new CompletableFuture<>();
    String url = instancesStorageUrl("") + "?query=" + urlEncode("item.barcode=706949453641* or title=Nod*");
    getClient().get(url, TENANT_ID, json(searchCompleted));
    return searchCompleted.get(10, SECONDS);
  }

  private void assertInstancesFoundInSearch(Response searchResponse, UUID firstInstanceId, UUID secondInstanceId) {
    assertThat(searchResponse.getStatusCode(), is(200));
    JsonObject responseBody = searchResponse.getJson();
    assertThat(responseBody.getInteger(TOTAL_RECORDS_KEY), is(2));

    List<JsonObject> foundInstances = toList(responseBody.getJsonArray(INSTANCES_KEY));
    assertThat(foundInstances.size(), is(2));

    Optional<JsonObject> firstInstance = foundInstances.stream()
      .filter(instance -> instance.getString("id").equals(firstInstanceId.toString()))
      .findFirst();
    Optional<JsonObject> secondInstance = foundInstances.stream()
      .filter(instance -> instance.getString("id").equals(secondInstanceId.toString()))
      .findFirst();

    assertThat("Instance with barcode should be found", firstInstance.isPresent(), is(true));
    assertThat("Instance with title and no holding or items, should be found", secondInstance.isPresent(), is(true));
  }

  @Test
  public void canSearchForFirstIsbnWithAdditionalHyphens() {
    canSort("isbn = 0-552-16754-1", "Interesting Times");
  }

  @Test
  public void canSearchForFirstIsbnWithAdditionalHyphenAndTruncation() {
    canSort("isbn = 05-5*", "Interesting Times");
  }

  @Test
  public void canSearchForSecondIsbnWithMissingHyphens() {
    canSort("isbn = 9780552167543", "Interesting Times");
  }

  @Test
  public void canSearchForSecondIsbnWithMissingHyphensAndTrunation() {
    canSort("isbn = 9780* sortBy title", "Interesting Times", "Temeraire");
  }

  @Test
  public void canSearchForSecondIsbnWithAlteredHyphens() {
    canSort("isbn = 9-7-8-055-2167-543", "Interesting Times");
  }

  @Test
  public void cannotFindIsbnWithTailString() {
    canSort("isbn = 552-16754-3");
  }

  @Test
  public void cannotFindIsbnWithInnerStringAndTruncation() {
    canSort("isbn = 552*");
  }

  @Test
  public void canFindFirstInvalidIsbn() {
    canSort("invalidIsbn = 12345", "Interesting Times");
  }

  @Test
  public void cannotFindIsbnInInvalidIsbn() {
    canSort("invalidIsbn = 0552167541");
  }

  @Test
  public void cannotFindInvalidIsbnInIsbn() {
    canSort("isbn = 12345");
  }

  @Test
  public void canSortAscending() {
    canSort("cql.allRecords=1 sortBy title",
      "Interesting Times", "Long Way to a Small Angry Planet", "Nod", "Temeraire", "Uprooted");
  }

  @Test
  public void canSortDescending() {
    canSort("cql.allRecords=1 sortBy title/sort.descending",
      "Uprooted", "Temeraire", "Nod", "Long Way to a Small Angry Planet", "Interesting Times");
  }

  // Interesting Times has two ISBNs: 0552167541, 978-0-552-16754-3

  @Test
  public void canDeleteAllInstances() throws InterruptedException, ExecutionException, TimeoutException {
    createInstance(smallAngryPlanet(UUID.randomUUID()));
    createInstance(nod(UUID.randomUUID()));
    createInstance(uprooted(UUID.randomUUID()));

    CompletableFuture<Response> allDeleted = new CompletableFuture<>();

    getClient().delete(instancesStorageUrl("?query=cql.allRecords=1"), TENANT_ID,
      ResponseHandler.empty(allDeleted));

    Response deleteResponse = allDeleted.get(10, SECONDS);

    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    getClient().get(instancesStorageUrl(""), TENANT_ID,
      json(getCompleted));

    Response response = getCompleted.get(10, SECONDS);

    JsonObject responseBody = response.getJson();

    JsonArray allInstances = responseBody.getJsonArray(INSTANCES_KEY);

    assertThat(allInstances.size(), is(0));
    assertThat(responseBody.getInteger(TOTAL_RECORDS_KEY), is(0));

    instanceMessageChecks.allInstancesDeletedMessagePublished();
  }

  @SneakyThrows
  @Test
  public void tenantIsRequiredForCreatingNewInstance() {
    JsonObject instance = nod(UUID.randomUUID());

    CompletableFuture<Response> postCompleted = new CompletableFuture<>();

    getClient().post(instancesStorageUrl(""), instance, null, ResponseHandler.any(postCompleted));

    Response response = postCompleted.get(10, SECONDS);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), is("Unable to process request Tenant must be set"));
  }

  @SneakyThrows
  @Test
  public void tenantIsRequiredForGettingAnInstance() {

    URL getInstanceUrl = instancesStorageUrl(format("/%s",
      UUID.randomUUID()));

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    getClient().get(getInstanceUrl, null, ResponseHandler.any(getCompleted));

    Response response = getCompleted.get(10, SECONDS);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), is("Unable to process request Tenant must be set"));
  }

  @SneakyThrows
  @Test
  public void tenantIsRequiredForGettingAllInstances() {
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    getClient().get(instancesStorageUrl(""), null, ResponseHandler.any(getCompleted));

    Response response = getCompleted.get(10, SECONDS);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), is("Unable to process request Tenant must be set"));
  }

  @SneakyThrows
  @Test
  public void testCrossTableQueries() {
    setupCrossTableQueryTestData();
    String[] queryUrls = buildCrossTableQueryUrls(instancesStorageUrl("") + "?query=");

    executeCrossTableQueriesAndVerify(queryUrls);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldReturnInstanceWhenFilterByTags() throws Exception {

    final String tagsKey = "tags";
    final String tagListKey = "tagList";
    final String tagValue = "important";
    final String searchByTagQuery = tagsKey + "." + tagListKey + "=" + tagValue;
    final JsonObject instanceWithTag = smallAngryPlanet(UUID.randomUUID())
      .put(tagsKey, new JsonObject().put(tagListKey, new JsonArray().add(tagValue)));
    createInstance(instanceWithTag);
    createInstance(nod(UUID.randomUUID()));

    CompletableFuture<Response> future = new CompletableFuture<>();

    getClient().get(instancesStorageUrl("") + "?query=" + urlEncode(searchByTagQuery), TENANT_ID, json(future));

    Response response = future.get(10, SECONDS);

    assertThat(response.getStatusCode(), is(HTTP_OK));

    JsonObject instancesJsonResponse = response.getJson();
    JsonArray instances = instancesJsonResponse.getJsonArray(INSTANCES_KEY);

    final LinkedHashMap<?, ?> instance = (LinkedHashMap<?, ?>) instances.getList().getFirst();
    final LinkedHashMap<String, ArrayList<String>> instanceTags =
      (LinkedHashMap<String, ArrayList<String>>) instance.get(tagsKey);

    assertThat(instances.size(), is(1));
    assertThat(instanceTags.get(tagListKey), hasItem(tagValue));
    assertThat(instancesJsonResponse.getInteger(TOTAL_RECORDS_KEY), is(1));
  }

  @Test
  public void cannotPostSyncInstancesWhenNumberOfInstancesExceedsMaxEntitiesProperty()
    throws InterruptedException, ExecutionException, TimeoutException {
    JsonArray instancesArray = new JsonArray();
    int numberOfInstances = MAX_ENTITIES + 1;

    for (int i = 0; i < numberOfInstances; i++) {
      instancesArray.add(smallAngryPlanet(UUID.randomUUID()).put("hrid", "hrid" + i));
    }

    JsonObject instanceCollection = new JsonObject().put(INSTANCES_KEY, instancesArray);

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();
    getClient().post(instancesStorageSyncUrl(""), instanceCollection, TENANT_ID,
      ResponseHandler.empty(createCompleted));
    assertThat(createCompleted.get(30, SECONDS), statusCodeIs(HttpURLConnection.HTTP_ENTITY_TOO_LARGE));
  }

  /**
   * Test case for instanceStatusUpdatedDateTrigger.sql trigger.
   */
  @Test
  public void shouldChangeInitialStatusUpdatedDate() throws Exception {
    UUID id = UUID.randomUUID();
    JsonObject instanceToCreate = smallAngryPlanet(id)
      .put("statusId", getOtherInstanceType().getId().toString());

    createInstance(instanceToCreate);

    Response createdInstance = getById(id);

    assertThat(createdInstance.getJson().getString(STATUS_UPDATED_DATE_PROPERTY), hasIsoFormat());

    final String initialDate = createdInstance.getJson().getString(STATUS_UPDATED_DATE_PROPERTY);

    JsonObject replacement = instanceToCreate.copy()
      .put("hrid", createdInstance.getJson().getString("hrid"))
      .put("statusId", getCatalogedInstanceType().getId().toString());

    JsonObject updatedInstance = updateInstance(replacement).getJson();

    assertThat(updatedInstance.getString(STATUS_UPDATED_DATE_PROPERTY), hasIsoFormat());

    assertThat(updatedInstance
      .getInstant(STATUS_UPDATED_DATE_PROPERTY), not(initialDate));
  }

  /**
   * Test case for instanceStatusUpdatedDateTrigger.sql trigger.
   */
  @Test
  public void shouldChangeStatusUpdatedDateOnSubsequentStatusChanges() throws Exception {
    UUID id = UUID.randomUUID();
    JsonObject instanceToCreate = smallAngryPlanet(id)
      .put("statusId", getOtherInstanceType().getId().toString());

    createInstance(instanceToCreate);
    Response createdInstance = getById(id);
    assertThat(createdInstance.getJson().getString(STATUS_UPDATED_DATE_PROPERTY), notNullValue());

    JsonObject updatedInstanceWithCatStatus = updateInstanceWithStatus(
      instanceToCreate, createdInstance, getCatalogedInstanceType().getId().toString());
    JsonObject updatedInstanceWithOthStatus = updateInstanceWithStatus(
      updatedInstanceWithCatStatus, createdInstance, getOtherInstanceType().getId().toString());

    verifyStatusUpdatedDateChanged(updatedInstanceWithCatStatus, updatedInstanceWithOthStatus);
  }

  /**
   * Test case for instanceStatusUpdatedDateTrigger.sql trigger.
   */
  @Test
  public void shouldNotChangeStatusUpdatedDateWhenStatusHasNotChanged() throws Exception {
    UUID id = UUID.randomUUID();
    JsonObject instanceToCreate = smallAngryPlanet(id)
      .put("statusId", getOtherInstanceType().getId().toString());

    createInstance(instanceToCreate);

    Response createdInstance = getById(id);
    assertThat(createdInstance.getJson().getString(STATUS_UPDATED_DATE_PROPERTY),
      hasIsoFormat());

    JsonObject instanceWithCatStatus = instanceToCreate.copy()
      .put("hrid", createdInstance.getJson().getString("hrid"))
      .put("statusId", getCatalogedInstanceType().getId().toString());
    JsonObject updatedInstanceWithCatStatus = updateInstance(instanceWithCatStatus)
      .getJson();

    JsonObject anotherInstanceWithCatStatus = updatedInstanceWithCatStatus.copy()
      .put("statusId", getCatalogedInstanceType().getId().toString());
    JsonObject updatedAnotherInstanceWithCatStatus =
      updateInstance(anotherInstanceWithCatStatus)
        .getJson();

    assertThat(updatedInstanceWithCatStatus.getString(STATUS_UPDATED_DATE_PROPERTY), hasIsoFormat());

    assertThat(updatedInstanceWithCatStatus.getString(STATUS_UPDATED_DATE_PROPERTY),
      is(updatedAnotherInstanceWithCatStatus.getString(STATUS_UPDATED_DATE_PROPERTY)));
    assertThat(updatedInstanceWithCatStatus
      .getInstant(STATUS_UPDATED_DATE_PROPERTY), withinSecondsBeforeNow(seconds(2)));
  }

  @Test
  public void canPostSynchronousBatch() throws Exception {
    log.info("Starting canPostSynchronousBatch");
    JsonArray instancesArray = new JsonArray();

    instancesArray.add(uprooted(UUID.randomUUID()));
    instancesArray.add(smallAngryPlanet(UUID.randomUUID()));
    instancesArray.add(temeraire(UUID.randomUUID()));

    JsonObject instanceCollection = new JsonObject().put(INSTANCES_KEY, instancesArray);

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();
    getClient().post(instancesStorageSyncUrl(""), instanceCollection, TENANT_ID,
      ResponseHandler.empty(createCompleted));
    assertThat(createCompleted.get(30, SECONDS), statusCodeIs(HTTP_CREATED));

    var createdInstances = instancesArray.stream()
      .map(JsonObject.class::cast)
      .map(json -> json.getString("id"))
      .map(this::getById)
      .map(Response::getJson)
      .toList();

    createdInstances.forEach(instance -> assertThat(instance.getBoolean(DISCOVERY_SUPPRESS), is(false)));

    instanceMessageChecks.createdMessagesPublished(createdInstances);
    log.info("Finished canPostSynchronousBatch");
  }

  @Test
  public void instancesCreatedInBatchSynchShouldHaveMetadata() throws Exception {
    JsonObject instanceCollection = createRequestForMultipleInstances(2);
    toList(instanceCollection.getJsonArray(INSTANCES_KEY)).forEach(instance ->
      assertFalse(instance.containsKey(METADATA_KEY)));

    final var createCompleted = createInstancesBatchSync(instanceCollection);
    assertThat(createCompleted.get(30, SECONDS), statusCodeIs(HTTP_CREATED));

    toList(instanceCollection.getJsonArray(INSTANCES_KEY)).forEach(this::assertMetadataExists);
  }

  @Test
  public void instancesCreatedInBatchShouldHaveStatusDate() throws Exception {
    JsonObject instanceCollection = createRequestForMultipleInstances(3);

    final var createCompleted = createInstancesBatchSync(instanceCollection);
    assertThat(createCompleted.get(30, SECONDS), statusCodeIs(HTTP_CREATED));

    toList(instanceCollection.getJsonArray(INSTANCES_KEY)).forEach(item ->
      assertThat(getById(item.getString("id")).getJson().getString(STATUS_UPDATED_DATE_PROPERTY),
        hasIsoFormat()));
  }

  @Test
  public void cannotBatchCreateInstancesWithNonExistingSubjectIds() throws Exception {
    var instanceCollection = createRequestForMultipleInstances(3);
    var instanceToCreate = smallAngryPlanet(UUID.randomUUID());
    var invalidSubjectId = UUID.randomUUID().toString();
    var subject = new Subject()
      .withSourceId(invalidSubjectId)
      .withValue("subject");
    var subjects = new JsonArray().add(subject);
    instanceToCreate.put(SUBJECTS_KEY, subjects);
    instanceCollection.getJsonArray(INSTANCES_KEY).add(instanceToCreate);
    CompletableFuture<Response> createCompleted = new CompletableFuture<>();
    getClient().post(instancesStorageSyncUrl(""), instanceCollection, TENANT_ID, ResponseHandler.json(createCompleted));
    var result = createCompleted.get(30, SECONDS);
    assertThat(result, allOf(
      statusCodeIs(HttpStatus.HTTP_UNPROCESSABLE_ENTITY),
      errorMessageContains(invalidSubjectId)
    ));
  }

  @Test
  public void cannotBatchCreateInstancesWithInvalidStatisticalCodeIds() throws Exception {
    var instanceCollection = createRequestForMultipleInstances(3);
    var instanceToCreate = smallAngryPlanet(UUID.randomUUID());

    instanceToCreate.put(STATISTICAL_CODE_IDS_KEY, Set.of(INVALID_VALUE));

    instanceCollection.getJsonArray(INSTANCES_KEY).add(instanceToCreate);
    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    getClient().post(instancesStorageSyncUrl(""), instanceCollection, TENANT_ID, ResponseHandler.text(createCompleted));
    var response = createCompleted.get(30, SECONDS);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), containsString(INVALID_TYPE_ERROR_MESSAGE));
  }

  @Test
  public void cannotPostSynchronousBatchWithInvalidInstance() {
    JsonArray instancesArray = new JsonArray();
    instancesArray.add(uprooted(UUID.randomUUID()));
    instancesArray.add(smallAngryPlanet(UUID.randomUUID()).put("invalidPropertyName", "bar"));
    instancesArray.add(temeraire(UUID.randomUUID()));
    JsonObject instanceCollection = new JsonObject().put(INSTANCES_KEY, instancesArray);

    Response response = instancesStorageSyncClient.attemptToCreate(instanceCollection);
    assertThat(response, allOf(
      statusCodeIs(HttpStatus.HTTP_UNPROCESSABLE_ENTITY),
      errorMessageContains("Unrecognized field \"invalidPropertyName\"")));

    for (int i = 0; i < instancesArray.size(); i++) {
      assertGetNotFound(instancesStorageUrl("/" + instancesArray.getJsonObject(i).getString("id")));
    }
  }

  @Test
  public void cannotPostSynchronousBatchWithExistingIdWithoutUpsertParameter() throws Exception {
    cannotPostSynchronousBatchWithExistingId("");
  }

  @Test
  public void cannotPostSynchronousBatchWithExistingIdUpsertFalse() throws Exception {
    cannotPostSynchronousBatchWithExistingId("?upsert=false");
  }

  @Test
  public void canPostSynchronousBatchWithExistingIdUpsertTrue() throws Exception {
    UUID duplicateId = UUID.randomUUID();
    final IndividualResource existingInstance = createInstance(nod(duplicateId));
    final JsonObject firstInstanceToCreate = uprooted(UUID.randomUUID());
    final JsonObject instanceToUpdate = smallAngryPlanet(duplicateId);
    final JsonObject secondInstanceToCreate = temeraire(UUID.randomUUID());

    JsonArray instancesArray = new JsonArray();
    instancesArray.add(firstInstanceToCreate);
    instancesArray.add(instanceToUpdate);
    instancesArray.add(secondInstanceToCreate);
    JsonObject instanceCollection = new JsonObject().put(INSTANCES_KEY, instancesArray);

    Response response = instancesStorageSyncClient.attemptToCreate("?upsert=true", instanceCollection);
    assertThat(response, statusCodeIs(HTTP_CREATED));

    verifyBatchUpsertResults(instancesArray, duplicateId, existingInstance,
      firstInstanceToCreate, secondInstanceToCreate);
  }

  @Test
  public void canPostSynchronousBatchWithoutIdsWithUpsertTrue() {
    canPostSynchronousBatchWithoutIds("?upsert=true");
  }

  @Test
  public void canPostSynchronousBatchWithoutIdsWithoutUpsert() {
    canPostSynchronousBatchWithoutIds("");
  }

  @Test
  public void cannotPostSynchronousBatchWithDuplicateId() {
    UUID duplicateId = UUID.randomUUID();
    JsonArray instancesArray = new JsonArray();
    instancesArray.add(uprooted(duplicateId));
    instancesArray.add(smallAngryPlanet(UUID.randomUUID()));
    instancesArray.add(temeraire(duplicateId));
    JsonObject instanceCollection = new JsonObject().put(INSTANCES_KEY, instancesArray);

    Response response = instancesStorageSyncClient.attemptToCreate(instanceCollection);
    assertThat(response, allOf(
      statusCodeIs(HttpStatus.HTTP_UNPROCESSABLE_ENTITY),
      anyOf(errorMessageContains("value already exists"), errorMessageContains("duplicate")),
      errorParametersValueIs(duplicateId.toString())));

    for (int i = 0; i < instancesArray.size(); i++) {
      assertGetNotFound(instancesStorageUrl("/" + instancesArray.getJsonObject(i).getString("id")));
    }
  }

  @Test
  public void cannotPostSynchronousBatchUnsafeIfNotAllowed() {
    // not allowed because env var DB_ALLOW_SUPPRESS_OPTIMISTIC_LOCKING is not set
    JsonArray instances = new JsonArray().add(uprooted(UUID.randomUUID())).add(temeraire(UUID.randomUUID()));
    assertThat(postSynchronousBatchUnsafe(instances), statusCodeIs(413));
  }

  @Test
  public void canPostSynchronousBatchUnsafe() {
    OptimisticLockingUtil.configureAllowSuppressOptimisticLocking(
      Map.of(OptimisticLockingUtil.DB_ALLOW_SUPPRESS_OPTIMISTIC_LOCKING, "9999-12-31T23:59:59Z"));

    // insert
    JsonArray instances = new JsonArray().add(uprooted(UUID.randomUUID())).add(temeraire(UUID.randomUUID()));
    assertThat(postSynchronousBatchUnsafe(instances), statusCodeIs(HTTP_CREATED));
    // unsafe update
    instances.getJsonObject(1).put("title", "surprise");
    assertThat(postSynchronousBatchUnsafe(instances), statusCodeIs(HTTP_CREATED));
    // safe update, env var should not influence the regular API
    instances.getJsonObject(1).put("title", "sunset");
    assertThat(postSynchronousBatch("?upsert=true", instances), statusCodeIs(409));
  }

  @Test
  public void canPostSynchronousBatchUnsafeWithInvalidStatisticalCodeIds() {
    OptimisticLockingUtil.configureAllowSuppressOptimisticLocking(
      Map.of(OptimisticLockingUtil.DB_ALLOW_SUPPRESS_OPTIMISTIC_LOCKING, "9999-12-31T23:59:59Z"));

    // insert
    JsonArray instances = new JsonArray().add(uprooted(UUID.randomUUID())).add(temeraire(UUID.randomUUID()));
    var invalidInstance = instances.getJsonObject(1);
    invalidInstance.put(STATISTICAL_CODE_IDS_KEY, Set.of(INVALID_VALUE));

    var response = postSynchronousBatchUnsafe(instances);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), containsString(INVALID_TYPE_ERROR_MESSAGE));
  }

  @Test
  public void canGenerateInstanceHridWhenNotSupplied() throws Exception {
    log.info("Starting canGenerateInstanceHRIDWhenNotSupplied");

    final UUID id = UUID.randomUUID();
    final JsonObject instanceToCreate = smallAngryPlanet(id);
    instanceToCreate.remove("hrid");

    setInstanceSequence(1);

    createInstance(instanceToCreate);

    final Response createdInstance = getById(id);

    assertThat(createdInstance.getJson().getString("hrid"), is("in00000000001"));

    log.info("Finished canGenerateInstanceHRIDWhenNotSupplied");
  }

  @Test
  public void canCreateInstanceWhenHridSupplied() throws Exception {
    log.info("Starting canCreateInstanceWhenHRIDSupplied");

    final String hrid = "testHRID";
    final UUID id = UUID.randomUUID();
    final JsonObject instanceToCreate = smallAngryPlanet(id);
    instanceToCreate.put("hrid", hrid);

    createInstance(instanceToCreate);

    final Response createdInstance = getById(id);

    assertThat(createdInstance.getJson().getString("hrid"), is(hrid));

    log.info("Finished canCreateInstanceWhenHRIDSupplied");
  }

  @Test
  public void cannotCreateInstanceWithDuplicateHrid() throws Exception {
    log.info("Starting cannotCreateInstanceWithDuplicateHRID");

    final UUID id = UUID.randomUUID();
    final JsonObject instanceToCreate = smallAngryPlanet(id);
    instanceToCreate.remove("hrid");

    setInstanceSequence(1);

    createInstance(instanceToCreate);

    final Response createdInstance = getById(id);

    assertThat(createdInstance.getJson().getString("hrid"), is("in00000000001"));

    final JsonObject instanceToCreateWithSameHrid = nod(UUID.randomUUID());
    instanceToCreateWithSameHrid.put("hrid", "in00000000001");

    final CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    getClient().post(instancesStorageUrl(""), instanceToCreateWithSameHrid, TENANT_ID,
      text(createCompleted));

    final Response response = createCompleted.get(10, SECONDS);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), is("HRID value already exists in table instance: in00000000001"));

    log.info("Finished cannotCreateInstanceWithDuplicateHRID");
  }

  @Test
  public void cannotCreateInstanceWithHridFailure() throws Exception {
    log.info("Starting cannotCreateInstanceWithHRIDFailure");

    final UUID id = UUID.randomUUID();
    final JsonObject instanceToCreate = smallAngryPlanet(id);
    instanceToCreate.remove("hrid");

    setInstanceSequence(99_999_999_999L);

    createInstance(instanceToCreate);

    final Response createdInstance = getById(id);

    assertThat(createdInstance.getJson().getString("hrid"), is("in99999999999"));

    final JsonObject instanceToFail = nod(UUID.randomUUID());
    instanceToFail.remove("hrid");

    final CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    getClient().post(instancesStorageUrl(""), instanceToFail, TENANT_ID, text(createCompleted));

    final Response response = createCompleted.get(10, SECONDS);

    assertThat(response.getStatusCode(), is(500));
    assertThat(response.getBody(), isMaximumSequenceValueError("hrid_instances_seq"));

    log.info("Finished cannotCreateInstanceWithHRIDFailure");
  }

  @Test
  public void cannotChangeHridAfterCreation() throws Exception {
    log.info("Starting cannotChageHRIDAfterCreation");

    final UUID id = UUID.randomUUID();
    final JsonObject instanceToCreate = smallAngryPlanet(id);
    instanceToCreate.remove("hrid");

    setInstanceSequence(1);

    createInstance(instanceToCreate);

    final JsonObject instance = getById(id).getJson();
    final String expectedHrid = "in00000000001";

    assertThat(instance.getString("hrid"), is(expectedHrid));

    instance.put("hrid", "testHRID");

    final CompletableFuture<Response> replaceCompleted = new CompletableFuture<>();

    getClient().put(instancesStorageUrl(format("/%s", id)), instance,
      TENANT_ID, ResponseHandler.text(replaceCompleted));

    final Response putResponse = replaceCompleted.get(10, SECONDS);

    assertThat(putResponse.getStatusCode(), is(400));
    assertThat(putResponse.getBody(),
      is("The hrid field cannot be changed: new=testHRID, old=in00000000001"));

    log.info("Finished cannotChageHRIDAfterCreation");
  }

  @Test
  public void allowChangeHridWhenSourceIsConsortia() throws Exception {
    log.info("Starting allowChangeHridWhenSourceIsConsortia");

    final UUID id = UUID.randomUUID();
    final JsonObject instanceToCreate = smallAngryPlanet(id).put("hrid", "oldHRID");
    createInstance(instanceToCreate);

    final String newHrid = "newHRID";
    final JsonObject instance = getById(id).getJson()
      .put("source", "CONSORTIUM-MARC").put("hrid", newHrid);

    final IndividualResource updateInstance =
      updateInstance(pojo2JsonObject(instance));

    assertThat(updateInstance.getJson().mapTo(Instance.class).getHrid(), is(newHrid));

    log.info("Finished allowChangeHridWhenSourceIsConsortia");
  }

  @Test
  @SneakyThrows
  public void cannotCreateAnInstanceWhenAlreadyAllocatedHridIsAllocated() {
    final var instanceRequest = smallAngryPlanet(UUID.randomUUID());
    instanceRequest.remove("id");
    instanceRequest.remove("hrid");

    setInstanceSequence(1000L);

    // Allocate the HRID
    final var firstAllocation = instancesClient.create(instanceRequest).getJson();

    assertThat(firstAllocation.getString("hrid"), is("in00000001000"));

    // Reset the sequence
    setInstanceSequence(1000L);

    // Attempt second allocation
    final CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    getClient().post(instancesStorageUrl(""), instanceRequest, TENANT_ID,
      text(createCompleted));

    final Response response = createCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), is(
      "HRID value already exists in table instance: in00000001000"));
  }

  @Test
  public void cannotRemoveHridAfterCreation() throws Exception {
    log.info("Starting cannotRemoveHRIDAfterCreation");

    final UUID id = UUID.randomUUID();
    final JsonObject instanceToCreate = smallAngryPlanet(id);
    instanceToCreate.remove("hrid");

    setInstanceSequence(1);

    createInstance(instanceToCreate);

    final JsonObject instance = getById(id).getJson();
    final String expectedHrid = "in00000000001";

    assertThat(instance.getString("hrid"), is(expectedHrid));

    instance.remove("hrid");

    final CompletableFuture<Response> replaceCompleted = new CompletableFuture<>();

    getClient().put(instancesStorageUrl(format("/%s", id)), instance,
      TENANT_ID, ResponseHandler.text(replaceCompleted));

    final Response putResponse = replaceCompleted.get(10, SECONDS);

    assertThat(putResponse.getStatusCode(), is(400));
    assertThat(putResponse.getBody(),
      is("The hrid field cannot be changed: new=null, old=in00000000001"));

    log.info("Finished cannotRemoveHRIDAfterCreation");
  }

  @Test
  public void canPostSynchronousBatchWithGeneratedHrid() throws Exception {
    log.info("Starting canPostSynchronousBatchWithGeneratedHRID");

    final JsonArray instancesArray = new JsonArray();

    instancesArray.add(uprooted(UUID.randomUUID()));
    instancesArray.add(smallAngryPlanet(UUID.randomUUID()));
    instancesArray.add(temeraire(UUID.randomUUID()));

    final JsonObject instanceCollection = new JsonObject().put(INSTANCES_KEY, instancesArray);

    setInstanceSequence(1);

    final CompletableFuture<Response> createCompleted = new CompletableFuture<>();
    getClient().post(instancesStorageSyncUrl(""), instanceCollection, TENANT_ID,
      ResponseHandler.empty(createCompleted));

    assertThat(createCompleted.get(30, SECONDS), statusCodeIs(HTTP_CREATED));

    for (int i = 0; i < 3; i++) {
      final JsonObject instance = instancesArray.getJsonObject(i);
      final Response response = getById(instance.getString("id"));
      assertThat(response, statusCodeIs(HttpStatus.HTTP_OK));
      assertThat(response.getJson().getString("hrid"),
        is(both(greaterThanOrEqualTo("in00000000001"))
          .and(lessThanOrEqualTo("in00000000003"))));
    }

    log.info("Finished canPostSynchronousBatchWithGeneratedHRID");
  }

  @Test
  public void canPostSynchronousBatchWithExistingAndGeneratedHrid() throws Exception {
    log.info("Starting canPostSynchronousBatchWithExistingAndGeneratedHRID");

    final UUID[] id = new UUID[5];
    final JsonArray instancesArray = new JsonArray();
    instancesArray.add(uprooted(id[0] = UUID.randomUUID()));
    instancesArray.add(uprooted(id[1] = UUID.randomUUID()).put("hrid", "foo"));
    instancesArray.add(uprooted(id[2] = UUID.randomUUID()));
    instancesArray.add(uprooted(id[3] = UUID.randomUUID()).put("hrid", "bar"));
    instancesArray.add(uprooted(id[4] = UUID.randomUUID()));

    final JsonObject instanceCollection = new JsonObject().put(INSTANCES_KEY, instancesArray);

    setInstanceSequence(1);

    instancesStorageSyncClient.createNoResponse(instanceCollection);

    assertThat(getById(id[0]).getJson().getString("hrid"), is("in00000000001"));
    assertThat(getById(id[1]).getJson().getString("hrid"), is("foo"));
    assertThat(getById(id[2]).getJson().getString("hrid"), is("in00000000002"));
    assertThat(getById(id[3]).getJson().getString("hrid"), is("bar"));
    assertThat(getById(id[4]).getJson().getString("hrid"), is("in00000000003"));

    String nextHrid = createInstance(uprooted(UUID.randomUUID())).getJson().getString("hrid");
    assertThat(nextHrid, is("in00000000004"));

    log.info("Finisted canPostSynchronousBatchWithExistingAndGeneratedHRID");
  }

  @Test
  public void cannotPostSynchronousBatchWithDuplicateHrids() {
    log.info("Starting cannotPostSynchronousBatchWithDuplicateHRIDs");

    final JsonArray instancesArray = new JsonArray();
    instancesArray.add(uprooted(UUID.randomUUID()));

    final JsonObject t = temeraire(UUID.randomUUID());
    t.put("hrid", "in00000000001");
    instancesArray.add(t);

    final JsonObject instanceCollection = new JsonObject().put(INSTANCES_KEY, instancesArray);
    setInstanceSequence(1);

    Response response = instancesStorageSyncClient.attemptToCreate(instanceCollection);
    assertThat(response, statusCodeIs(HttpStatus.HTTP_UNPROCESSABLE_ENTITY));

    verifyDuplicateHridError(response, "in00000000001");

    log.info("Finished cannotPostSynchronousBatchWithDuplicateHRIDs");
  }

  @Test
  public void cannotPostSynchronousBatchWithHridFailure() {
    log.info("Starting cannotPostSynchronousBatchWithHRIDFailure");

    final JsonArray instancesArray = new JsonArray();
    instancesArray.add(uprooted(UUID.randomUUID()));
    final JsonObject t = temeraire(UUID.randomUUID());
    t.put("hrid", "");
    instancesArray.add(t);

    final JsonObject instanceCollection = new JsonObject().put(INSTANCES_KEY, instancesArray);

    setInstanceSequence(99_999_999_999L);

    Response response = instancesStorageSyncClient.attemptToCreate(instanceCollection);

    assertThat(response, statusCodeIs(HttpStatus.HTTP_INTERNAL_SERVER_ERROR));
    assertThat(response.getBody(), isMaximumSequenceValueError("hrid_instances_seq"));

    log.info("Finished cannotPostSynchronousBatchWithHRIDFailure");
  }

  @Test
  public void cannotCreateInstanceWithDuplicateMatchKey() throws Exception {
    log.info("Starting cannotCreateInstanceWithDuplicateMatchKey");

    final UUID id = UUID.randomUUID();
    final JsonObject instanceToCreate = smallAngryPlanet(id);
    instanceToCreate.put("matchKey", "match_key");

    setInstanceSequence(1);

    createInstance(instanceToCreate);

    final Response createdInstance = getById(id);

    assertThat(createdInstance.getJson().getString("matchKey"), is("match_key"));

    final JsonObject instanceToCreateWithSameMatchKey = nod(UUID.randomUUID());
    instanceToCreateWithSameMatchKey.put("matchKey", "match_key");

    final CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    getClient().post(instancesStorageUrl(""), instanceToCreateWithSameMatchKey, TENANT_ID,
      text(createCompleted));

    final Response response = createCompleted.get(10, SECONDS);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), is("lower(f_unaccent(jsonb ->> 'matchKey'::text)) value already "
      + "exists in table instance: match_key"));

    log.info("Finished cannotCreateInstanceWithDuplicateMatchKey");
  }

  @Test
  public void canPostSynchronousBatchWithDiscoverySuppressedInstances() {
    final JsonArray instancesArray = new JsonArray();
    final UUID smallAngryPlanetId = UUID.randomUUID();
    final UUID uprootedId = UUID.randomUUID();

    instancesArray.add(uprooted(uprootedId));
    instancesArray.add(smallAngryPlanet(smallAngryPlanetId)
      .put(DISCOVERY_SUPPRESS, true));

    final JsonObject instanceCollection = new JsonObject().put(INSTANCES_KEY,
      instancesArray);

    Response createResponse = instancesStorageSyncClient
      .attemptToCreate(instanceCollection);

    assertThat(createResponse.getStatusCode(), is(201));

    assertSuppressedFromDiscovery(smallAngryPlanetId.toString());
    assertNotSuppressedFromDiscovery(uprootedId.toString());
  }

  @Test
  public void canPostDiscoverySuppressedInstance() throws Exception {
    IndividualResource instance = createInstance(smallAngryPlanet(UUID.randomUUID())
      .put(DISCOVERY_SUPPRESS, true));

    assertThat(instance.getJson().getBoolean(DISCOVERY_SUPPRESS), is(true));
    assertSuppressedFromDiscovery(instance.getId().toString());
  }

  @Test
  public void canUpdateInstanceWithDiscoverySuppressProperty() throws Exception {
    IndividualResource instance = createInstance(smallAngryPlanet(UUID.randomUUID()));
    assertThat(instance.getJson().getBoolean(DISCOVERY_SUPPRESS), is(false));

    // Clear Kafka events after create to reduce chances of
    // CREATE messages appearing after UPDATE later on.
    // This should be removed once the messaging problem is
    // properly resolved.
    removeAllEvents();

    final IndividualResource updateInstance = updateInstance(
      getById(instance.getId()).getJson().copy().put(DISCOVERY_SUPPRESS, true));

    assertSuppressedFromDiscovery(instance.getId().toString());

    instanceMessageChecks.updatedMessagePublished(instance.getJson(), updateInstance.getJson());
  }

  @Test
  public void canSearchByDiscoverySuppressProperty() throws Exception {
    final IndividualResource suppressedInstance = createInstance(smallAngryPlanet(UUID.randomUUID())
      .put(DISCOVERY_SUPPRESS, true));
    final IndividualResource notSuppressedInstance = createInstance(
      smallAngryPlanet(UUID.randomUUID()));

    final List<IndividualResource> suppressedInstances = instancesClient
      .getMany("%s==true", DISCOVERY_SUPPRESS);
    final List<IndividualResource> notSuppressedInstances = instancesClient
      .getMany("%s==false", DISCOVERY_SUPPRESS);

    assertThat(suppressedInstances.size(), is(1));
    assertThat(suppressedInstances.getFirst().getId(), is(suppressedInstance.getId()));

    assertThat(notSuppressedInstances.size(), is(1));
    assertThat(notSuppressedInstances.getFirst().getId(), is(notSuppressedInstance.getId()));
  }

  @Test
  public void canSearchByStaffSuppressProperty() throws Exception {
    final IndividualResource suppressedInstance = createInstance(smallAngryPlanet(UUID.randomUUID())
      .put(STAFF_SUPPRESS, true));
    final IndividualResource notSuppressedInstance = createInstance(
      smallAngryPlanet(UUID.randomUUID())
        .put(STAFF_SUPPRESS, false));
    final IndividualResource notSuppressedInstanceDefault = createInstance(
      smallAngryPlanet(UUID.randomUUID()));

    final List<IndividualResource> suppressedInstances = instancesClient
      .getMany("%s==true", STAFF_SUPPRESS);
    final List<IndividualResource> notSuppressedInstances = instancesClient
      .getMany("cql.allRecords=1 not %s==true", STAFF_SUPPRESS);

    assertThat(suppressedInstances.size(), is(1));
    assertThat(suppressedInstances.getFirst().getId(), is(suppressedInstance.getId()));

    assertThat(notSuppressedInstances.size(), is(2));
    assertThat(notSuppressedInstances.stream()
        .map(IndividualResource::getId)
        .toList(),
      containsInAnyOrder(notSuppressedInstance.getId(), notSuppressedInstanceDefault.getId()));
  }

  /**
   * Insert n records into instance table where the title field is build using
   * prefix and the number from 1 .. n.
   */
  private void insert(TestContext testContext, PostgresClient pg, String prefix, int n) {
    Async async = testContext.async();
    String table = PostgresClient.convertToPsqlStandard(TENANT_ID) + ".instance";
    String sql = "INSERT INTO " + table
      + " SELECT uuid, json_build_object('title', prefix || n, 'id', uuid)"
      + " FROM (SELECT n, prefix, md5(prefix || n)::uuid AS uuid"
      + "       FROM (SELECT generate_series(1, " + n + ") AS n, '" + prefix + " ' AS prefix) AS tmp1"
      + "      ) AS tmp2";

    pg.execute(sql, testContext.asyncAssertSuccess(updated -> {
      testContext.assertEquals(n, updated.rowCount());
      async.complete();
    }));
    async.await(10000 /* ms */);
  }

  private MarcJson toMarcJson(String resourcePath) throws IOException {
    String mrcjson = IOUtils.toString(Objects.requireNonNull(this.getClass().getResourceAsStream(resourcePath)), UTF_8);
    JsonObject json = new JsonObject(mrcjson);
    MarcJson newMarcJson = new MarcJson();
    newMarcJson.setLeader(json.getString("leader"));
    @SuppressWarnings("unchecked")
    List<Object> fields = json.getJsonArray("fields").getList();
    newMarcJson.setFields(fields);
    return newMarcJson;
  }

  private void put(UUID id, MarcJson marcJson, HttpStatus expectedStatus) throws Exception {
    CompletableFuture<Response> putCompleted = new CompletableFuture<>();
    getClient().put(instancesStorageUrl("/" + id + "/source-record/marc-json"), marcJson,
      TENANT_ID, ResponseHandler.empty(putCompleted));
    Response response = putCompleted.get(10, SECONDS);
    assertThat(response.getStatusCode(), is(expectedStatus.toInt()));
  }

  private void put(UUID id, MarcJson marcJson) throws Exception {
    put(id, marcJson, HttpStatus.HTTP_NO_CONTENT);
  }

  private String getSourceRecordFormat(UUID instanceId) throws Exception {
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    getClient().get(instancesStorageUrl("/" + instanceId),
      TENANT_ID, json(getCompleted));
    Response getResponse = getCompleted.get(10, SECONDS);
    assertThat(getResponse.getStatusCode(), is(200));
    return getResponse.getJson().getString("sourceRecordFormat");
  }

  private Response getMarcJson(UUID id) throws Exception {
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    getClient().get(instancesStorageUrl("/" + id + "/source-record/marc-json"),
      TENANT_ID, json(getCompleted));
    return getCompleted.get(10, SECONDS);
  }

  private void getMarcJsonNotFound(UUID id) {
    assertGetNotFound(instancesStorageUrl("/" + id + "/source-record/marc-json"));
  }

  /**
   * Run a get request using the provided cql query.
   * Example: searchForInstances("title = t*");
   * The example runs an API request with "?query=title+%3D+t*"
   *
   * @return the response as an JsonObject
   */
  private JsonObject searchForInstances(String cql) {
    return searchForInstances(cql, -1, -1);
  }

  /**
   * Run a get request using the provided cql query and the provided offset and limit values
   * (a negative value means no offset or no limit).
   * Example 1: searchForInstances("title = t*", -1, -1);
   * Example 2: searchForInstances("title = t*", 30, 10);
   * The examples runs an API request with "?query=title+%3D+t*" and "?query=title+%3D+t*&offset=30&limit=10"
   *
   * @return the response as an JsonObject
   */
  private JsonObject searchForInstances(String cql, int offset, int limit) {
    try {
      CompletableFuture<Response> searchCompleted = new CompletableFuture<>();

      String url = instancesStorageUrl("") + "?query=" + urlEncode(cql);
      if (offset >= 0) {
        url += "&offset=" + offset;
      }
      if (limit >= 0) {
        url += "&limit=" + limit;
      }

      getClient().get(url, TENANT_ID, json(searchCompleted));
      Response searchResponse = searchCompleted.get(10, SECONDS);

      assertThat(searchResponse, statusCodeIs(200));
      return searchResponse.getJson();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Create the 5 example instances.
   */
  private void create5instances() {
    try {
      createInstance(smallAngryPlanet(UUID.randomUUID()));
      createInstance(nod(UUID.randomUUID()));
      createInstance(uprooted(UUID.randomUUID()));
      createInstance(temeraire(UUID.randomUUID()));
      createInstance(interestingTimes(UUID.randomUUID()));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Create the 5 example instances and run a get request using the provided cql query.
   * Example: searchForInstancesWithin5("title = t*");
   * The example runs an API request with "?query=title+%3D+t*" against the
   * 5 example instances.
   *
   * @return the response as an JsonObject
   */
  private JsonObject searchForInstancesWithin5(String cql) {
    create5instances();
    // RMB ensures en_US locale so that sorting behaves that same in all environments
    return searchForInstances(cql);
  }

  /**
   * Assert that the jsonObject contains an instances Array where each array element
   * has one title String with the expectedTitles in the correct order.
   */
  private void matchInstanceTitles(JsonObject jsonObject, String... expectedTitles) {
    JsonArray foundInstances = jsonObject.getJsonArray(INSTANCES_KEY);
    String[] titles = new String[foundInstances.size()];
    for (int i = 0; i < titles.length; i++) {
      titles[i] = foundInstances.getJsonObject(i).getString("title");
    }
    assertThat(titles, is(expectedTitles));
    assertThat(TOTAL_RECORDS_KEY, jsonObject.getInteger(TOTAL_RECORDS_KEY), is(expectedTitles.length));
  }

  /**
   * Assert that the cql query returns the expectedTitles in that order.
   * Searches within the 5 example instance records.
   *
   * @param cql            query to run
   * @param expectedTitles titles in the expected order
   */
  private void canSort(String cql, String... expectedTitles) {
    JsonObject searchBody = searchForInstancesWithin5(cql);
    matchInstanceTitles(searchBody, expectedTitles);
  }

  private void cannotPostSynchronousBatchWithExistingId(String subPath) throws Exception {
    UUID duplicateId = UUID.randomUUID();
    JsonArray instancesArray = new JsonArray();
    instancesArray.add(uprooted(UUID.randomUUID()));
    instancesArray.add(smallAngryPlanet(duplicateId));
    instancesArray.add(temeraire(UUID.randomUUID()));
    JsonObject instanceCollection = new JsonObject().put(INSTANCES_KEY, instancesArray);

    createInstance(instancesArray.getJsonObject(1));

    Response response = instancesStorageSyncClient.attemptToCreate(subPath, instanceCollection);
    assertThat(response, allOf(
      statusCodeIs(HttpStatus.HTTP_UNPROCESSABLE_ENTITY),
      anyOf(errorMessageContains("value already exists"), errorMessageContains("duplicate"))));

    assertGetNotFound(instancesStorageUrl("/" + instancesArray.getJsonObject(0).getString("id")));
    assertExists(instancesArray.getJsonObject(1));
    assertGetNotFound(instancesStorageUrl("/" + instancesArray.getJsonObject(2).getString("id")));
  }

  private void canPostSynchronousBatchWithoutIds(String postBatchParams) {
    var instancesArray = new JsonArray();
    var instanceRecord = new JsonObject()
      .put("source", "MARC")
      .put("title", "Test-Instance")
      .put("instanceTypeId", UUID_INSTANCE_TYPE.toString())
      .put("_version", 1);
    instancesArray.add(instanceRecord);
    assertThat(postSynchronousBatch(postBatchParams, instancesArray), statusCodeIs(HTTP_CREATED));
    var getCompleted = new CompletableFuture<Response>();
    getClient().get(instancesStorageUrl("") + "?query=title=Test-Instance", TENANT_ID, json(getCompleted));
    var response = TestBase.get(getCompleted);
    assertThat(response, statusCodeIs(HTTP_OK));
    var createdInstance = response.getJson().getJsonArray("instances").getJsonObject(0);
    assertThat(createdInstance.getString("id"), notNullValue());
    assertThat(createdInstance.getString("title"), equalTo("Test-Instance"));
    instanceMessageChecks.createdMessagePublished(getById(createdInstance.getString("id")).getJson());
  }

  private void createInstancesWithClassificationNumbers() throws InterruptedException,
    ExecutionException, TimeoutException {

    final UUID firstInstanceId = UUID.randomUUID();
    final UUID secondInstanceId = UUID.randomUUID();
    final UUID classificationTypeId = UUID.randomUUID();

    JsonObject firstClassifications = new JsonObject();
    firstClassifications.put("classificationTypeId", classificationTypeId.toString());
    firstClassifications.put("classificationNumber", "K1 .M385");

    JsonObject secondClassifications = new JsonObject();
    secondClassifications.put("classificationTypeId", classificationTypeId.toString());
    secondClassifications.put("classificationNumber", "KB1 .A437");

    JsonObject firstInstanceToCreate = smallAngryPlanet(firstInstanceId)
      .put("classifications", new JsonArray()
        .add(firstClassifications));
    JsonObject secondInstanceToCreate = nod(secondInstanceId)
      .put("classifications", new JsonArray()
        .add(secondClassifications));

    createInstance(firstInstanceToCreate);
    createInstance(secondInstanceToCreate);
  }

  private JsonObject createRequestForMultipleInstances(Integer numberOfInstances) {
    JsonArray instancesArray = new JsonArray();

    for (int i = 0; i < numberOfInstances; i++) {
      instancesArray.add(smallAngryPlanet(UUID.randomUUID()));
    }
    return new JsonObject().put(INSTANCES_KEY, instancesArray);
  }

  private CompletableFuture<Response> createInstancesBatchSync(JsonObject batchRequest) {
    CompletableFuture<Response> createCompleted = new CompletableFuture<>();
    getClient().post(instancesStorageSyncUrl(""), batchRequest, TENANT_ID, ResponseHandler.empty(createCompleted));
    return createCompleted;
  }

  private void setInstanceSequence(long sequenceNumber) {
    final PostgresClient postgresClient =
      PostgresClient.getInstance(getVertx(), TENANT_ID);
    final CompletableFuture<Void> sequenceSet = new CompletableFuture<>();

    getVertx().runOnContext(v -> postgresClient.selectSingle("select setval('hrid_instances_seq',"
        + sequenceNumber + ",FALSE)",
      r -> {
        if (r.succeeded()) {
          sequenceSet.complete(null);
        } else {
          sequenceSet.completeExceptionally(r.cause());
        }
      }));

    try {
      sequenceSet.get(2, SECONDS);
    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  private void createHoldings(JsonObject holdingsToCreate)
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    getClient().post(holdingsStorageUrl(""), holdingsToCreate,
      Map.of(XOkapiHeaders.URL, mockServer.baseUrl()), TENANT_ID, json(createCompleted));

    Response response = createCompleted.get(2, SECONDS);

    assertThat(format("Create holdings failed: %s", response.getBody()),
      response.getStatusCode(), is(201));
  }

  private IndividualResource createInstance(JsonObject instanceToCreate)
    throws InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    getClient().post(instancesStorageUrl(""), instanceToCreate,
      TENANT_ID, json(createCompleted));

    Response response = createCompleted.get(5, SECONDS);

    assertThat(format("Create instance failed: %s", response.getBody()),
      response.getStatusCode(), is(201));

    return new IndividualResource(response);
  }

  private Response update(JsonObject instance) {
    final UUID id = UUID.fromString(instance.getString("id"));
    CompletableFuture<Response> replaceCompleted = new CompletableFuture<>();

    getClient().put(instancesStorageUrl(format("/%s", id)), instance,
      TENANT_ID, ResponseHandler.empty(replaceCompleted));

    try {
      return replaceCompleted.get(10, SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  private IndividualResource updateInstance(JsonObject instance) {
    Response putResponse = update(instance);
    assertThat(putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    Response getResponse = getById(instance.getString("id"));
    assertThat(getResponse.getStatusCode(), is(HTTP_OK));

    return new IndividualResource(getResponse);
  }

  private Response postSynchronousBatchUnsafe(JsonArray itemsArray) {
    return postSynchronousBatch(instancesStorageSyncUnsafeUrl(""), itemsArray);
  }

  private Response postSynchronousBatch(String subPath, JsonArray itemsArray) {
    return postSynchronousBatch(instancesStorageSyncUrl(subPath), itemsArray);
  }

  private Response postSynchronousBatch(URL url, JsonArray itemsArray) {
    JsonObject instanceCollection = new JsonObject().put("instances", itemsArray);
    CompletableFuture<Response> createCompleted = new CompletableFuture<>();
    getClient().post(url, instanceCollection, TENANT_ID, ResponseHandler.any(createCompleted));
    try {
      return createCompleted.get(5, SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  private Response getById(UUID id) {
    return getById(id.toString());
  }

  private Response getById(String id) {
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    getClient().get(instancesStorageUrl(format("/%s", id)), TENANT_ID, json(getCompleted));
    try {
      return getCompleted.get(10, SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  private void assertExists(JsonObject expectedInstance) {
    Response response = getById(expectedInstance.getString("id"));
    assertThat(response, statusCodeIs(HttpStatus.HTTP_OK));
    assertThat(response.getBody(), containsString(expectedInstance.getString("title")));
  }

  private void assertNotExists(JsonObject instanceToGet) {
    assertGetNotFound(instancesStorageUrl("/" + instanceToGet.getString("id")));
  }

  private JsonObject nod(UUID id) {
    JsonArray identifiers = new JsonArray();
    identifiers.add(identifier(UUID_ASIN, "B01D1PLMDO"));
    JsonArray contributors = new JsonArray();
    contributors.add(contributor(UUID_PERSONAL_NAME, "Barnes, Adrian"));
    JsonArray tags = new JsonArray();
    tags.add("test-tag");

    return createInstanceRequest(id, "MARC", "Nod",
      identifiers, contributors, UUID_INSTANCE_TYPE, tags);
  }

  private JsonObject uprooted(UUID id) {
    JsonArray identifiers = new JsonArray();
    identifiers.add(identifier(UUID_ISBN, "1447294149"));
    identifiers.add(identifier(UUID_ISBN, "9781447294146"));

    JsonArray contributors = new JsonArray();
    contributors.add(contributor(UUID_PERSONAL_NAME, "Novik, Naomi"));

    JsonArray tags = new JsonArray();
    tags.add("test-tag");

    return createInstanceRequest(id, "MARC", "Uprooted",
      identifiers, contributors, UUID_INSTANCE_TYPE, tags);
  }

  private JsonObject temeraire(UUID id) {

    JsonArray identifiers = new JsonArray();
    identifiers.add(identifier(UUID_ISBN, "0007258712"));
    identifiers.add(identifier(UUID_ISBN, "9780007258710"));

    JsonArray contributors = new JsonArray();
    contributors.add(contributor(UUID_PERSONAL_NAME, "Novik, Naomi"));

    JsonArray tags = new JsonArray();
    tags.add("test-tag");
    return createInstanceRequest(id, "MARC", "Temeraire",
      identifiers, contributors, UUID_INSTANCE_TYPE, tags);
  }

  private JsonObject interestingTimes(UUID id) {
    JsonArray identifiers = new JsonArray();
    identifiers.add(identifier(UUID_ISBN, "0552167541"));
    identifiers.add(identifier(UUID_ISBN, "978-0-552-16754-3"));
    identifiers.add(identifier(UUID_INVALID_ISBN, "1-2-3-4-5"));

    JsonArray contributors = new JsonArray();
    contributors.add(contributor(UUID_PERSONAL_NAME, "Pratchett, Terry"));

    JsonArray tags = new JsonArray();
    tags.add("test-tag");
    return createInstanceRequest(id, "MARC", "Interesting Times",
      identifiers, contributors, UUID_INSTANCE_TYPE, tags);
  }

  private NatureOfContentTerm createNatureOfContentTerm(final String name)
    throws InterruptedException, ExecutionException, TimeoutException {
    NatureOfContentTerm natureOfContentTerm = new NatureOfContentTerm()
      .withId(UUID.randomUUID().toString())
      .withName(name)
      .withSource("test");

    CompletableFuture<Response> createNatureOfContent =
      new CompletableFuture<>();

    getClient().post(natureOfContentTermsUrl(""), natureOfContentTerm,
      TENANT_ID, json(createNatureOfContent));

    Response response = createNatureOfContent.get(10, SECONDS);
    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    natureOfContentIdsToRemoveAfterTest.add(natureOfContentTerm.getId());
    return response.getJson().mapTo(NatureOfContentTerm.class);
  }

  private void assertNotSuppressedFromDiscovery(String id) {
    assertThat(getById(id).getJson().getBoolean(DISCOVERY_SUPPRESS), is(false));
  }

  private void assertSuppressedFromDiscovery(String id) {
    assertThat(getById(id).getJson().getBoolean(DISCOVERY_SUPPRESS), is(true));
  }

  private void assertMetadataExists(JsonObject instance) {
    var metadata = getById(instance.getString("id")).getJson().getJsonObject(METADATA_KEY);
    assertNotNull(metadata);
    assertNotNull(metadata.getString("createdDate"));
    assertNotNull(metadata.getString("updatedDate"));
  }

  private String[] createNatureOfContentTerms() throws InterruptedException, ExecutionException, TimeoutException {
    NatureOfContentTerm journalContentType = createNatureOfContentTerm("journal_test");
    NatureOfContentTerm bookContentType = createNatureOfContentTerm("book_test");

    return Stream.of(journalContentType, bookContentType)
      .map(NatureOfContentTerm::getId)
      .toArray(String[]::new);
  }

  private JsonObject createInstanceWithAllFields(UUID id, String[] natureOfContentIds, String adminNote) {
    var dates = new InstanceDates()
      .withDateTypeId(UUID_INSTANCE_DATE_TYPE.toString())
      .withDate1("2023")
      .withDate2("2024");
    var subject = new Subject()
      .withSourceId(UUID_INSTANCE_SUBJECT_SOURCE_ID.toString())
      .withTypeId(UUID_INSTANCE_SUBJECT_TYPE_ID.toString())
      .withValue("subject");
    var subjects = new JsonArray().add(subject);
    JsonObject instanceToCreate = smallAngryPlanet(id);
    instanceToCreate.put("natureOfContentTermIds", Arrays.asList(natureOfContentIds));
    instanceToCreate.put("administrativeNotes", new JsonArray().add(adminNote));
    instanceToCreate.put(DATES_KEY, pojo2JsonObject(dates));
    instanceToCreate.put(SUBJECTS_KEY, subjects);
    return instanceToCreate;
  }

  private void verifyCreatedInstanceFields(JsonObject instance, UUID id, String[] natureOfContentIds,
                                           String adminNote) {
    assertThat(instance.getString("id"), is(id.toString()));
    assertThat(instance.getString("title"), is("Long Way to a Small Angry Planet"));
    assertThat(instance.getBoolean("previouslyHeld"), is(false));
    assertThat(instance.getJsonArray("administrativeNotes").contains(adminNote), is(true));

    JsonArray identifiers = instance.getJsonArray("identifiers");
    assertThat(identifiers.size(), is(1));
    assertThat(identifiers, hasItem(identifierMatches(UUID_ISBN.toString(), "9781473619777")));
    assertThat(instance.getJsonArray("natureOfContentTermIds"),
      containsInAnyOrder(natureOfContentIds));
    assertThat(instance.getBoolean(DISCOVERY_SUPPRESS), is(false));
  }

  private void verifyRetrievedInstanceFields(JsonObject instanceFromGet, String[] natureOfContentIds) {
    assertThat(instanceFromGet.getString("title"),
      is("Long Way to a Small Angry Planet"));

    JsonArray identifiersFromGet = instanceFromGet.getJsonArray("identifiers");
    assertThat(identifiersFromGet.size(), is(1));
    assertThat(identifiersFromGet, hasItem(identifierMatches(UUID_ISBN.toString(), "9781473619777")));

    @SuppressWarnings("unchecked")
    List<String> tags = instanceFromGet.getJsonObject("tags").getJsonArray("tagList").getList();

    assertThat(tags.size(), is(1));
    assertThat(tags, hasItem(TAG_VALUE));
    assertThat(instanceFromGet.getJsonArray("natureOfContentTermIds"),
      containsInAnyOrder(natureOfContentIds));

    assertThat(
      instanceFromGet.getString(STATUS_UPDATED_DATE_PROPERTY), hasIsoFormat());

    assertThat(instanceFromGet.getBoolean(DISCOVERY_SUPPRESS), is(false));
  }

  private void verifyInstanceDatesAndSubjects(JsonObject instance) {
    var storedDates = instance.getJsonObject(DATES_KEY)
      .mapTo(InstanceDates.class);

    assertThat(storedDates.getDateTypeId(), is(UUID_INSTANCE_DATE_TYPE.toString()));
    assertThat(storedDates.getDate1(), is("2023"));
    assertThat(storedDates.getDate2(), is("2024"));

    var storedSubject = instance.getJsonArray(SUBJECTS_KEY).getJsonObject(0).mapTo(Subject.class);

    assertThat(storedSubject.getSourceId(), is(UUID_INSTANCE_SUBJECT_SOURCE_ID.toString()));
    assertThat(storedSubject.getTypeId(), is(UUID_INSTANCE_SUBJECT_TYPE_ID.toString()));
    assertThat(storedSubject.getValue(), is("subject"));
  }

  private JsonObject updateInstanceWithStatus(JsonObject instanceTemplate, Response createdInstance, String statusId) {
    JsonObject instanceWithStatus = instanceTemplate.copy()
      .put("hrid", createdInstance.getJson().getString("hrid"))
      .put("statusId", statusId);
    return updateInstance(instanceWithStatus).getJson();
  }

  private void verifyStatusUpdatedDateChanged(JsonObject first, JsonObject second) {
    assertThat(first.getString(STATUS_UPDATED_DATE_PROPERTY),
      not(second.getString(STATUS_UPDATED_DATE_PROPERTY)));

    assertThat(first.getString(STATUS_UPDATED_DATE_PROPERTY), hasIsoFormat());
    assertThat(second.getString(STATUS_UPDATED_DATE_PROPERTY), hasIsoFormat());

    assertThat(first.getInstant(STATUS_UPDATED_DATE_PROPERTY), withinSecondsBeforeNow(seconds(3)));
    assertThat(second.getInstant(STATUS_UPDATED_DATE_PROPERTY), withinSecondsBeforeNow(seconds(2)));
  }

  private void setupCrossTableQueryTestData() throws Exception {
    JsonArray identifiers = new JsonArray();
    identifiers.add(identifier(UUID_ISBN, "9781473619777"));
    JsonArray contributors = new JsonArray();
    contributors.add(contributor(UUID_PERSONAL_NAME, "Chambers, Becky"));
    JsonArray tags = new JsonArray();
    tags.add("test-tag");

    UUID idJ1 = UUID.randomUUID();
    UUID idJ2 = UUID.randomUUID();
    UUID idJ3 = UUID.randomUUID();

    createInstance(createInstanceRequest(idJ1, "TEST1", "Long Way to a Small Angry Planet 1",
      identifiers, contributors, UUID_INSTANCE_TYPE, tags));
    createInstance(createInstanceRequest(idJ2, "TEST2", "Long Way to a Small Angry Planet 2",
      identifiers, contributors, UUID_INSTANCE_TYPE, tags));
    createInstance(createInstanceRequest(idJ3, "TEST3", "Long Way to a Small Angry Planet 3",
      identifiers, contributors, UUID_INSTANCE_TYPE, tags));

    UUID loc1 = UUID.fromString("11111111-dee7-48eb-b03f-d02fdf0debd0");
    LocationUtility.createLocation(loc1, "location1", "IX/L1");
    UUID loc2 = UUID.fromString("99999999-dee7-48eb-b03f-d02fdf0debd0");
    LocationUtility.createLocation(loc2, "location2", "IX/L2");

    createHoldings(createHoldingsRecord(idJ1.toString(), loc1.toString()));
    createHoldings(createHoldingsRecord(idJ2.toString(), loc2.toString()));
    createHoldings(createHoldingsRecord(idJ3.toString(), loc2.toString()));
  }

  private JsonObject createHoldingsRecord(String instanceId, String locationId) {
    JsonObject holding = new JsonObject();
    holding.put("id", UUID.randomUUID().toString());
    holding.put("instanceId", instanceId);
    holding.put("sourceId", getPreparedHoldingSourceId().toString());
    holding.put("permanentLocationId", locationId);
    return holding;
  }

  private String[] buildCrossTableQueryUrls(String baseUrl) {
    return new String[] {
      baseUrl + urlEncode("title=Long Way to a Small Angry Planet* sortby title"),
      baseUrl + urlEncode("title=cql.allRecords=1 sortBy title"),
      baseUrl + urlEncode("holdingsRecords.permanentLocationId=99999999-dee7-48eb-b03f-d02fdf0debd0 sortBy title"),
      baseUrl + urlEncode("title=cql.allRecords=1 sortby title"),
      baseUrl + urlEncode("title=cql.allRecords=1 and holdingsRecords.permanentLocationId="
        + "99999999-dee7-48eb-b03f-d02fdf0debd0 sortby title"),
      baseUrl + urlEncode("title=cql.allRecords=1 and holdingsRecords.permanentLocationId=abc* sortby"
        + " holdingsRecords.permanentLocationId")
    };
  }

  @SneakyThrows
  private void executeCrossTableQueriesAndVerify(String[] urls) {
    for (int i = 0; i < urls.length; i++) {
      CompletableFuture<Response> cf = new CompletableFuture<>();
      getClient().get(urls[i], TENANT_ID, json(cf));

      Response cqlResponse = cf.get(10, SECONDS);
      assertThat(cqlResponse.getStatusCode(), is(HTTP_OK));
      System.out.println(cqlResponse.getBody()
        + "\nStatus - " + cqlResponse.getStatusCode() + " at " + System.currentTimeMillis() + " for " + urls[i]);

      verifyCrossTableQueryResult(i, cqlResponse);
    }
  }

  private void verifyCrossTableQueryResult(int queryIndex, Response response) {
    JsonObject json = response.getJson();
    if (queryIndex == 0 || queryIndex == 1) {
      assertThat(3, is(json.getInteger(TOTAL_RECORDS_KEY)));
      assertThat("TEST1", is(json.getJsonArray(INSTANCES_KEY).getJsonObject(0).getString("source")));
    } else if (queryIndex == 2) {
      assertThat(2, is(json.getInteger(TOTAL_RECORDS_KEY)));
      assertThat("TEST2", is(json.getJsonArray(INSTANCES_KEY).getJsonObject(0).getString("source")));
    } else if (queryIndex == 3) {
      assertThat("TEST1", is(json.getJsonArray(INSTANCES_KEY).getJsonObject(0).getString("source")));
    } else if (queryIndex == 4) {
      assertThat(2, is(json.getInteger(TOTAL_RECORDS_KEY)));
    } else {
      assertThat(0, is(json.getInteger(TOTAL_RECORDS_KEY)));
    }
  }

  private void verifyBatchUpsertResults(JsonArray instancesArray, UUID duplicateId,
                                        IndividualResource existingInstance,
                                        JsonObject firstInstanceToCreate,
                                        JsonObject secondInstanceToCreate) throws Exception {
    assertExists(instancesArray.getJsonObject(0));
    assertExists(instancesArray.getJsonObject(1));
    assertExists(instancesArray.getJsonObject(2));

    Response getResponse = getById(duplicateId);
    assertThat(getResponse.getStatusCode(), is(HTTP_OK));
    JsonObject updatedInstance = getResponse.getJson();
    assertThat(updatedInstance.getString("title"), is("Long Way to a Small Angry Planet"));

    instanceMessageChecks.updatedMessagePublished(existingInstance.getJson(), updatedInstance);
    instanceMessageChecks.createdMessagePublished(getById(firstInstanceToCreate.getString("id")).getJson());
    instanceMessageChecks.createdMessagePublished(getById(secondInstanceToCreate.getString("id")).getJson());
  }

  private void verifyDuplicateHridError(Response response, String expectedHrid) {
    final Errors errors = response.getJson().mapTo(Errors.class);

    assertThat(errors, notNullValue());
    assertThat(errors.getErrors(), hasSize(1));
    var error = errors.getErrors().getFirst();
    assertThat(error.getMessage(),
      anyOf(containsString("value already exists"), containsString("duplicate key")));
    assertThat(error.getParameters(), notNullValue());
    var parameter = error.getParameters().getFirst();
    assertThat(parameter, notNullValue());
    assertThat(parameter.getKey(), containsString("'hrid'"));
    assertThat(parameter.getValue(), is(expectedHrid));
  }

  @Test
  public void canPatchAnInstanceUnlinkSubjectSourceAndType() {

    UUID id = UUID.randomUUID();
    var subject = new Subject()
      .withSourceId(UUID_INSTANCE_SUBJECT_SOURCE_ID.toString())
      .withTypeId(UUID_INSTANCE_SUBJECT_TYPE_ID.toString())
      .withValue("subject");
    var subjects = new JsonArray().add(subject);
    JsonObject instanceToCreate = smallAngryPlanet(id);
    instanceToCreate.put(SUBJECTS_KEY, subjects);

    var newId = createInstanceRecord(instanceToCreate);

    assertThat(newId, is(notNullValue()));

    var getResponse = getById(newId);

    assertThat(getResponse.getStatusCode(), is(HTTP_OK));
    var instanceFromGet = getResponse.getJson();
    instanceFromGet.put(SUBJECTS_KEY, null);

    var patchJson = new JsonObject();
    patchJson.put("title", "New Title");
    var expectedJson = getResponse.getJson().put("title", "New Title").remove("metadata");

    var updatedResponse = patch(newId.toString(), patchJson);
    assertThat(updatedResponse.getStatusCode(), is(HTTP_NO_CONTENT));

    getResponse = getById(newId);
    assertThat(getResponse.getJson().remove("metadata"), is(expectedJson));
  }

  private Response patch(String id, JsonObject patchJson) {
    CompletableFuture<Response> replaceCompleted = new CompletableFuture<>();

    getClient().patch(instancesStorageUrl(format("/%s", id)), patchJson,
      TENANT_ID, ResponseHandler.empty(replaceCompleted));

    try {
      return replaceCompleted.get(10, SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }
}
