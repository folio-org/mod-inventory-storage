package org.folio.rest.api;

import static java.lang.String.format;
import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static org.folio.rest.support.ResponseHandler.json;
import static org.folio.rest.support.http.InterfaceUrls.instancesBulk;
import static org.folio.rest.support.http.InterfaceUrls.instancesStorageUrl;
import static org.folio.services.s3storage.FolioS3ClientFactory.S3_ACCESS_KEY_ID_CONFIG;
import static org.folio.services.s3storage.FolioS3ClientFactory.S3_BUCKET_CONFIG;
import static org.folio.services.s3storage.FolioS3ClientFactory.S3_IS_AWS_CONFIG;
import static org.folio.services.s3storage.FolioS3ClientFactory.S3_REGION_CONFIG;
import static org.folio.services.s3storage.FolioS3ClientFactory.S3_SECRET_ACCESS_KEY_CONFIG;
import static org.folio.services.s3storage.FolioS3ClientFactory.S3_URL_CONFIG;
import static org.folio.utility.ModuleUtility.getClient;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.apache.commons.io.FileUtils;
import org.folio.rest.api.entities.PrecedingSucceedingTitle;
import org.folio.rest.jaxrs.model.BulkUpsertRequest;
import org.folio.rest.jaxrs.model.BulkUpsertResponse;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.support.IndividualResource;
import org.folio.rest.support.Response;
import org.folio.rest.support.messages.InstanceEventMessageChecks;
import org.folio.s3.client.FolioS3Client;
import org.folio.s3.client.S3ClientFactory;
import org.folio.s3.client.S3ClientProperties;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

public class InstanceStorageInstancesBulkApiTest extends TestBaseWithInventoryUtil {

  private static final String BULK_INSTANCES_PATH = "src/test/resources/instances/bulk/bulkInstances.ndjson";
  private static final String BULK_INSTANCES_WITH_INVALID_TYPE_PATH =
    "src/test/resources/instances/bulk/bulkInstancesWithInvalidInstanceType.ndjson";
  private static final String BULK_INSTANCES_WITH_INVALID_PRECEDING_TITLE_PATH =
    "src/test/resources/instances/bulk/bulkInstancesWithInvalidPrecedingTitle.ndjson";
  private static final String MINIO_BUCKET = "test-bucket";
  private static final String BULK_FILE_TO_UPLOAD = "parentLocation/filePath/bulkInstances";
  private static final String INSTANCE_TITLE_1 = "Long Way to a Small Angry Planet";
  private static final String INSTANCE_TITLE_2 = "Novik, Naomi";
  private static final String PRECEDING_SUCCEEDING_TITLE_TABLE = "preceding_succeeding_title";
  private static final String ID_FIELD = "id";
  private static final String ADMINISTRATIVE_NOTES_FIELD = "administrativeNotes";
  private static final String INVALID_INSTANCE_TYPE_ID_ERROR_MSG = "does not exist in instance_type.id.";

  private static LocalStackContainer localStackContainer;
  private static FolioS3Client s3Client;

  private final InstanceEventMessageChecks instanceMessageChecks = new InstanceEventMessageChecks(KAFKA_CONSUMER);

  @BeforeClass
  public static void setUpClass() {
    localStackContainer = new LocalStackContainer(DockerImageName.parse("localstack/localstack:s3-latest"))
      .withServices(S3);

    localStackContainer.start();
    System.setProperty(S3_URL_CONFIG, localStackContainer.getEndpoint().toString());
    System.setProperty(S3_REGION_CONFIG, localStackContainer.getRegion());
    System.setProperty(S3_ACCESS_KEY_ID_CONFIG, localStackContainer.getAccessKey());
    System.setProperty(S3_SECRET_ACCESS_KEY_CONFIG, localStackContainer.getSecretKey());
    System.setProperty(S3_BUCKET_CONFIG, MINIO_BUCKET);
    System.setProperty(S3_IS_AWS_CONFIG, Boolean.FALSE.toString());

    s3Client = S3ClientFactory.getS3Client(
      S3ClientProperties
        .builder()
        .endpoint(localStackContainer.getEndpoint().toString())
        .accessKey(localStackContainer.getAccessKey())
        .secretKey(localStackContainer.getSecretKey())
        .bucket(MINIO_BUCKET)
        .awsSdk(false)
        .region(localStackContainer.getRegion())
        .build()
    );
    s3Client.createBucketIfNotExists();
  }

  @AfterClass
  public static void tearDownClass() {
    localStackContainer.close();
  }

  @Before
  public void setUp() {
    StorageTestSuite.deleteAll(TENANT_ID, PRECEDING_SUCCEEDING_TITLE_TABLE);
    clearData();
    removeAllEvents();
  }

  @Test
  public void shouldUpdateInstancesWithoutErrors()
    throws ExecutionException, InterruptedException, TimeoutException, IOException {
    shouldUpdateInstances(true);
  }

  @Test
  public void shouldUpdateInstancesWithoutErrorsAndDoNotPublishDomainEvents()
    throws ExecutionException, InterruptedException, TimeoutException, IOException {
    shouldUpdateInstances(false);
  }

  @Test
  public void shouldUpdateInstancesWithErrors()
    throws ExecutionException, InterruptedException, TimeoutException, IOException {
    // given
    String expectedErrorRecordsFileName = BULK_FILE_TO_UPLOAD + "_failedEntities";
    String expectedErrorsFileName = BULK_FILE_TO_UPLOAD + "_errors";

    List<String> instancesIds = extractInstancesIdsFromFile(BULK_INSTANCES_WITH_INVALID_TYPE_PATH);
    FileInputStream inputStream = FileUtils.openInputStream(new File(BULK_INSTANCES_WITH_INVALID_TYPE_PATH));
    String bulkFilePath = s3Client.write(BULK_FILE_TO_UPLOAD, inputStream);

    final IndividualResource existingInstance1 = createInstance(buildInstance(instancesIds.get(0), INSTANCE_TITLE_1));
    final IndividualResource existingInstance2 = createInstance(buildInstance(instancesIds.get(1), INSTANCE_TITLE_2));

    // when
    BulkUpsertResponse bulkResponse = postInstancesBulk(new BulkUpsertRequest()
      .withRecordsFileName(bulkFilePath)
    );

    // then
    assertThat(bulkResponse.getErrorsNumber(), is(1));
    assertThat(bulkResponse.getErrorRecordsFileName(), is(expectedErrorRecordsFileName));
    assertThat(bulkResponse.getErrorsFileName(), is(expectedErrorsFileName));

    List<String> filesList = s3Client.list(BULK_FILE_TO_UPLOAD);
    assertThat(filesList.size(), is(3));
    assertThat(filesList, containsInAnyOrder(bulkFilePath, expectedErrorRecordsFileName, expectedErrorsFileName));
    List<String> errors = readLinesFromInputStream(s3Client.read(expectedErrorsFileName));
    assertThat(errors.size(), is(1));
    assertTrue(errors.getFirst().contains(INVALID_INSTANCE_TYPE_ID_ERROR_MSG));

    JsonObject updatedInstance1 = getInstanceById(existingInstance1.getId().toString());

    instanceMessageChecks.updatedMessagePublished(existingInstance1.getJson(), updatedInstance1);
    instanceMessageChecks.noUpdatedMessagePublished(existingInstance2.getId().toString());
  }

  @Test
  public void shouldUpdateInstancesInTransaction()
    throws ExecutionException, InterruptedException, TimeoutException, IOException {
    // given
    String expectedErrorRecordsFileName = BULK_FILE_TO_UPLOAD + "_failedEntities";
    String expectedErrorsFileName = BULK_FILE_TO_UPLOAD + "_errors";

    List<String> instancesIds = extractInstancesIdsFromFile(BULK_INSTANCES_WITH_INVALID_PRECEDING_TITLE_PATH);
    FileInputStream inputStream = FileUtils.openInputStream(new File(BULK_INSTANCES_WITH_INVALID_PRECEDING_TITLE_PATH));
    String bulkFilePath = s3Client.write(BULK_FILE_TO_UPLOAD, inputStream);

    final IndividualResource existingInstance1 = createInstance(buildInstance(instancesIds.get(0), INSTANCE_TITLE_1));
    final IndividualResource existingInstance2 = createInstance(buildInstance(instancesIds.get(1), INSTANCE_TITLE_2));

    // when
    BulkUpsertResponse bulkResponse = postInstancesBulk(new BulkUpsertRequest()
      .withRecordsFileName(bulkFilePath)
    );

    // then
    assertThat(bulkResponse.getErrorsNumber(), is(2));
    assertThat(bulkResponse.getErrorRecordsFileName(), is(expectedErrorRecordsFileName));
    assertThat(bulkResponse.getErrorsFileName(), is(expectedErrorsFileName));

    verifyErrorFilesAndContent(bulkFilePath, expectedErrorRecordsFileName, expectedErrorsFileName);
    verifyInstancesNotUpdated(existingInstance1, existingInstance2);
  }

  @Test
  public void shouldReturnUnprocessableEntityIfRecordsFileNameIsNotSpecified()
    throws ExecutionException, InterruptedException, TimeoutException {
    CompletableFuture<Response> future = getClient().post(instancesBulk(), new BulkUpsertRequest(), TENANT_ID);
    Response response = future.get(10, SECONDS);
    assertThat(response.getStatusCode(), is(HTTP_UNPROCESSABLE_ENTITY.toInt()));
  }

  private void shouldUpdateInstances(boolean publishEvents)
    throws IOException, InterruptedException, ExecutionException, TimeoutException {
    // given
    List<String> instancesIds = extractInstancesIdsFromFile(BULK_INSTANCES_PATH);
    FileInputStream inputStream = FileUtils.openInputStream(new File(BULK_INSTANCES_PATH));
    String bulkFilePath = s3Client.write(BULK_INSTANCES_PATH, inputStream);

    final IndividualResource existingInstance1 = createInstance(buildInstance(instancesIds.get(0), INSTANCE_TITLE_1));
    final IndividualResource existingInstance2 = createInstance(buildInstance(instancesIds.get(1), INSTANCE_TITLE_2));

    createPrecedingSucceedingTitles(existingInstance2);

    // when
    BulkUpsertResponse bulkResponse = postInstancesBulk(new BulkUpsertRequest()
      .withRecordsFileName(bulkFilePath)
      .withPublishEvents(publishEvents)
    );

    // then
    assertThat(bulkResponse.getErrorsNumber(), is(0));
    assertThat(bulkResponse.getErrorRecordsFileName(), nullValue());
    assertThat(bulkResponse.getErrorsFileName(), nullValue());

    verifyUpdatedInstancesAndTitles(existingInstance1, existingInstance2, publishEvents);
  }

  private List<String> extractInstancesIdsFromFile(String bulkInstancesFilePath) throws IOException {
    return Files.readAllLines(Path.of(bulkInstancesFilePath))
      .stream()
      .map(JsonObject::new)
      .map(json -> json.getString(ID_FIELD))
      .toList();
  }

  private JsonObject buildInstance(String id, String title) {
    JsonArray tags = JsonArray.of("test-tag");
    JsonObject instanceJson = createInstanceRequest(
      UUID.fromString(id), "MARC", title, new JsonArray(), new JsonArray(), UUID_INSTANCE_TYPE, tags);
    return instanceJson.put(ADMINISTRATIVE_NOTES_FIELD, JsonArray.of("test-note"));
  }

  private IndividualResource createInstance(JsonObject instanceToCreate) {
    CompletableFuture<Response> createCompleted = new CompletableFuture<>();
    getClient().post(instancesStorageUrl(""), instanceToCreate,
      TENANT_ID, json(createCompleted));

    Response response = TestBase.get(createCompleted);
    assertThat(format("Create instance failed: %s", response.getBody()),
      response.getStatusCode(), is(HTTP_CREATED));

    return new IndividualResource(response);
  }

  private BulkUpsertResponse postInstancesBulk(BulkUpsertRequest bulkRequest)
    throws InterruptedException, ExecutionException, TimeoutException {
    CompletableFuture<Response> future = getClient().post(instancesBulk(), bulkRequest, TENANT_ID);
    Response response = future.get(10, SECONDS);
    assertThat(response.getStatusCode(), is(HTTP_CREATED));

    return Json.decodeValue(response.getBody(), BulkUpsertResponse.class);
  }

  private JsonObject getInstanceById(String id) {
    Response response = instancesClient.getById(UUID.fromString(id));
    assertThat(response.getStatusCode(), is(HTTP_OK));
    return response.getJson();
  }

  private void assertNotControlledByMarcFields(JsonObject expectedInstanceJson, JsonObject actualInstanceJson) {
    Instance expectedInstance = expectedInstanceJson.mapTo(Instance.class);
    Instance actualInstance = actualInstanceJson.mapTo(Instance.class);
    assertEquals(expectedInstance.getTags(), actualInstance.getTags());
    assertEquals(expectedInstance.getAdministrativeNotes(), actualInstance.getAdministrativeNotes());
  }

  private List<JsonObject> getPrecedingSucceedingTitlesByInstanceId(UUID instanceId) {
    return precedingSucceedingTitleClient.getByQuery(
      format("?query=succeedingInstanceId==%1$s+or+precedingInstanceId==%1$s", instanceId));
  }

  private List<String> readLinesFromInputStream(InputStream inputStream) throws IOException {
    try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream))) {
      return bufferedReader.lines().toList();
    }
  }

  private void verifyErrorFilesAndContent(String bulkFilePath, String expectedErrorRecordsFileName,
                                          String expectedErrorsFileName) throws IOException {
    var filesList = s3Client.list(BULK_FILE_TO_UPLOAD);
    assertThat(filesList.size(), is(3));
    assertThat(filesList, containsInAnyOrder(bulkFilePath, expectedErrorRecordsFileName, expectedErrorsFileName));
    var errors = readLinesFromInputStream(s3Client.read(expectedErrorsFileName));
    assertThat(errors.size(), is(2));
    errors.forEach(error -> {
      assertFalse(error.contains("optimistic locking"));
      assertTrue(error.contains("violates foreign key constraint \"precedinginstanceid_instance_fkey\""));
    });
  }

  private void createPrecedingSucceedingTitles(IndividualResource existingInstance) {
    var precedingSucceedingTitle1 = new PrecedingSucceedingTitle(
      existingInstance.getId().toString(), null, "Houston oil directory", null, null);
    precedingSucceedingTitleClient.create(precedingSucceedingTitle1.getJson());
    var precedingSucceedingTitle2 = new PrecedingSucceedingTitle(
      existingInstance.getId().toString(), null, "International trade statistics", null, null);
    precedingSucceedingTitleClient.create(precedingSucceedingTitle2.getJson());
  }

  private void verifyUpdatedInstancesAndTitles(IndividualResource existingInstance1,
                                                IndividualResource existingInstance2,
                                                boolean publishEvents) {
    var updatedInstance1 = getInstanceById(existingInstance1.getId().toString());
    var updatedInstance2 = getInstanceById(existingInstance2.getId().toString());
    assertNotControlledByMarcFields(existingInstance1.getJson(), updatedInstance1);
    assertNotControlledByMarcFields(existingInstance2.getJson(), updatedInstance2);

    var updatedTitles = getPrecedingSucceedingTitlesByInstanceId(existingInstance2.getId());
    updatedTitles.forEach(titleJson -> {
      assertThat(titleJson.getString("succeedingInstanceId"), equalTo(existingInstance2.getId().toString()));
      assertThat(titleJson.getString("precedingInstanceId"), nullValue());
      assertThat(titleJson.getString("title"), notNullValue());
    });

    if (publishEvents) {
      instanceMessageChecks.updatedMessagePublished(existingInstance1.getJson(), updatedInstance1);
      instanceMessageChecks.updatedMessagePublished(existingInstance2.getJson(), updatedInstance2);
    } else {
      instanceMessageChecks.noUpdatedMessagePublished(existingInstance1.getId().toString());
      instanceMessageChecks.noUpdatedMessagePublished(existingInstance2.getId().toString());
    }
  }

  private void verifyInstancesNotUpdated(IndividualResource existingInstance1,
                                          IndividualResource existingInstance2) {
    var instance1 = getInstanceById(existingInstance1.getId().toString());
    var instance2 = getInstanceById(existingInstance2.getId().toString());
    assertEquals(1, instance1.getInteger("_version").intValue());
    assertEquals(1, instance2.getInteger("_version").intValue());

    var titles1 = getPrecedingSucceedingTitlesByInstanceId(existingInstance1.getId());
    var titles2 = getPrecedingSucceedingTitlesByInstanceId(existingInstance2.getId());
    assertEquals(0, titles1.size());
    assertEquals(0, titles2.size());

    instanceMessageChecks.noUpdatedMessagePublished(existingInstance1.getId().toString());
    instanceMessageChecks.noUpdatedMessagePublished(existingInstance2.getId().toString());
  }
}
