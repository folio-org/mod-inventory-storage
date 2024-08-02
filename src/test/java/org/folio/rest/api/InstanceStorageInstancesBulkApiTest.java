package org.folio.rest.api;

import static java.lang.String.format;
import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.rest.support.ResponseHandler.json;
import static org.folio.rest.support.http.InterfaceUrls.instancesBulk;
import static org.folio.rest.support.http.InterfaceUrls.instancesStorageUrl;
import static org.folio.utility.ModuleUtility.getClient;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.apache.commons.io.FileUtils;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.InstanceBulkRequest;
import org.folio.rest.jaxrs.model.InstanceBulkResponse;
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

  public static final String BULK_INSTANCES_PATH = "src/test/resources/instances/bulk/bulkInstances.ndjson";
  public static final String BULK_INSTANCES_WITH_INVALID_TYPE_PATH =
    "src/test/resources/instances/bulk/bulkInstancesWithInvalidInstanceType.ndjson";
  private static final String MINIO_BUCKET = "test-bucket";
  public static final String BULK_FILE_TO_UPLOAD = "parentLocation/filePath/bulkInstances";

  private static LocalStackContainer localStackContainer;

  private final InstanceEventMessageChecks instanceMessageChecks = new InstanceEventMessageChecks(KAFKA_CONSUMER);

  private static FolioS3Client s3Client;

  @BeforeClass
  public static void setUpClass() {
    localStackContainer = new LocalStackContainer(DockerImageName.parse("localstack/localstack:0.11.3"))
      .withServices(LocalStackContainer.Service.S3);

    localStackContainer.start();
    System.setProperty("AWS_URL", localStackContainer.getEndpoint().toString());
    System.setProperty("AWS_REGION", localStackContainer.getRegion());
    System.setProperty("AWS_ACCESS_KEY_ID", localStackContainer.getAccessKey());
    System.setProperty("AWS_SECRET_ACCESS_KEY", localStackContainer.getSecretKey());
    System.setProperty("AWS_BUCKET", MINIO_BUCKET);
    System.setProperty("AWS_SDK", Boolean.FALSE.toString());

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

  @Before
  public void setUp() {
    clearData();
    removeAllEvents();
  }

  @AfterClass
  public static void tearDownClass() {
    localStackContainer.close();
  }

  @Test
  public void shouldUpdateInstancesWithoutErrors() throws ExecutionException, InterruptedException, TimeoutException, IOException {
    List<String> instancesIds;
    try (BufferedReader bufferedReader = Files.newBufferedReader(Path.of(
      BULK_INSTANCES_PATH))) {
      instancesIds = bufferedReader
        .lines()
        .map(JsonObject::new)
        .map(json -> json.getString("id"))
        .toList();
    }

    FileInputStream inputStream = FileUtils.openInputStream(new File(BULK_INSTANCES_PATH));
    String bulkFilePath = s3Client.write(BULK_INSTANCES_PATH, inputStream);

    IndividualResource existingInstance1 = createInstance(smallAngryPlanet(UUID.fromString(instancesIds.get(0))));
    IndividualResource existingInstance2 = createInstance(uprooted(UUID.fromString(instancesIds.get(1))));

    InstanceBulkRequest bulkRequest = new InstanceBulkRequest()
      .withRecordsFileName(bulkFilePath);

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();
    getClient().post(instancesBulk(), bulkRequest, TENANT_ID, json(createCompleted));

    Response response = createCompleted.get(30, SECONDS);
    assertThat(response.getStatusCode(), is(HTTP_CREATED));

    InstanceBulkResponse bulkResponse = Json.decodeValue(response.getBody(), InstanceBulkResponse.class);
    assertThat(bulkResponse.getErrorsNumber(), is(0));
    assertThat(bulkResponse.getErrorRecordsFileName(), nullValue());
    assertThat(bulkResponse.getErrorsFileName(), nullValue());

    Response updatedInstance1 = instancesClient.getById(existingInstance1.getId());
    assertThat(updatedInstance1.getStatusCode(), is(HTTP_OK));
    Response updatedInstance2 = instancesClient.getById(existingInstance2.getId());
    assertThat(updatedInstance1.getStatusCode(), is(HTTP_OK));

    instanceMessageChecks.updatedMessagePublished(existingInstance1.getJson(), updatedInstance1.getJson());
    instanceMessageChecks.updatedMessagePublished(existingInstance2.getJson(), updatedInstance2.getJson());
  }

  @Test
  public void shouldUpdateInstancesWithErrors() throws ExecutionException, InterruptedException, TimeoutException, IOException {
    String expectedErrorRecordsFileName = BULK_FILE_TO_UPLOAD + "_failedEntities";
    String expectedErrorsFileName = BULK_FILE_TO_UPLOAD + "_errors";
    List<String> instancesIds;

    try (BufferedReader bufferedReader = Files.newBufferedReader(Path.of(BULK_INSTANCES_WITH_INVALID_TYPE_PATH))) {
      instancesIds = bufferedReader
        .lines()
        .map(JsonObject::new)
        .map(json -> json.getString("id"))
        .toList();
    }

    FileInputStream inputStream = FileUtils.openInputStream(new File(BULK_INSTANCES_WITH_INVALID_TYPE_PATH));
    String bulkFilePath = s3Client.write(BULK_FILE_TO_UPLOAD, inputStream);

    IndividualResource existingInstance1 = createInstance(smallAngryPlanet(UUID.fromString(instancesIds.get(0))));
    IndividualResource existingInstance2 = createInstance(uprooted(UUID.fromString(instancesIds.get(1))));

    InstanceBulkRequest bulkRequest = new InstanceBulkRequest().withRecordsFileName(bulkFilePath);

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();
    getClient().post(instancesBulk(), bulkRequest, TENANT_ID, json(createCompleted));
    Response response = createCompleted.get(30, SECONDS);
    assertThat(response.getStatusCode(), is(HTTP_CREATED));

    InstanceBulkResponse bulkResponse = Json.decodeValue(response.getBody(), InstanceBulkResponse.class);
    assertThat(bulkResponse.getErrorsNumber(), is(1));
    assertThat(bulkResponse.getErrorRecordsFileName(), is(expectedErrorRecordsFileName));
    assertThat(bulkResponse.getErrorsFileName(), is(expectedErrorsFileName));

    List<String> filesList = s3Client.list(BULK_FILE_TO_UPLOAD);
    assertThat(filesList.size(), is(3));
    assertThat(filesList, containsInAnyOrder(bulkFilePath, expectedErrorRecordsFileName, expectedErrorsFileName));
    List<String> errors = new BufferedReader(new InputStreamReader(s3Client.read(expectedErrorsFileName))).lines().toList();
    assertThat(errors.size(), is(1));

    Response updatedInstance1 = instancesClient.getById(existingInstance1.getId());
    assertThat(updatedInstance1.getStatusCode(), is(HTTP_OK));
    instanceMessageChecks.updatedMessagePublished(existingInstance1.getJson(), updatedInstance1.getJson());
    instanceMessageChecks.noUpdatedMessagePublished(existingInstance2.getId().toString());
  }

  private IndividualResource createInstance(JsonObject instanceToCreate) throws InterruptedException, ExecutionException, TimeoutException {
    CompletableFuture<Response> createCompleted = new CompletableFuture<>();
    getClient().post(instancesStorageUrl(""), instanceToCreate,
      TENANT_ID, json(createCompleted));

    Response response = createCompleted.get(2, SECONDS);

    assertThat(format("Create instance failed: %s", response.getBody()),
      response.getStatusCode(), is(HTTP_CREATED));

    return new IndividualResource(response);
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

}
