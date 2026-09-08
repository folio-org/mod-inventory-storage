package org.folio.rest.support;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.apache.commons.io.FileUtils;
import org.folio.rest.jaxrs.model.BulkUpsertRequest;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.services.BulkProcessingContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BulkProcessingErrorFileWriterTest {

  private static final String BULK_INSTANCES_FILE_PATH = "/parent-folder/bulkInstances";

  private final Vertx vertx = Vertx.vertx();
  private BulkProcessingContext bulkContext;
  private BulkProcessingErrorFileWriter writer;

  @BeforeEach
  void setUp() {
    var request = new BulkUpsertRequest().withRecordsFileName(BULK_INSTANCES_FILE_PATH);
    bulkContext = new BulkProcessingContext(request);
    writer = new BulkProcessingErrorFileWriter(vertx, bulkContext);
  }

  @AfterEach
  void tearDown() throws Exception {
    FileUtils.deleteDirectory(Path.of(bulkContext.getErrorsFileLocalPath()).getName(0).toFile());
  }

  @Test
  void shouldWriteEntityAndErrorMessageToFiles()
    throws Exception {
    // given
    String errorMessage = "Test error";
    Instance instance = new Instance().withId(UUID.randomUUID().toString());
    String expectedErrorEntitiesFileRecord = Json.encode(instance) + System.lineSeparator();
    String expectedErrorFileRecord = String.format("%s, %s%s", instance.getId(), errorMessage, System.lineSeparator());

    // when
    Future<Void> future = writer.initialize()
      .compose(v -> writer.write(instance, Instance::getId, new RuntimeException(errorMessage)))
      .compose(v -> writer.close());

    // then
    future.toCompletionStage().toCompletableFuture().get(10, SECONDS);
    assertFileContentEquals(bulkContext.getErrorEntitiesFileLocalPath(), expectedErrorEntitiesFileRecord);
    assertFileContentEquals(bulkContext.getErrorsFileLocalPath(), expectedErrorFileRecord);
  }

  @Test
  void shouldThrowExceptionOnWriteIfWriterIsNotInitialized() {
    Instance instance = new Instance().withId(UUID.randomUUID().toString());
    assertThrows(IllegalStateException.class, () ->
      writer.write(instance, Instance::getId, new RuntimeException("Test error")));
  }

  private void assertFileContentEquals(String filePath, String expectedContent) throws IOException {
    Path path = Path.of(filePath);
    assertTrue(Files.exists(path, NOFOLLOW_LINKS));
    String actualFileContent = Files.readString(path);
    assertEquals(expectedContent, actualFileContent);
  }
}
