package org.folio.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.folio.rest.jaxrs.model.BulkUpsertRequest;
import org.junit.jupiter.api.Test;

class BulkProcessingContextTest {

  private static final String BULK_INSTANCES_FILE_PATH = "/parent-folder/bulkInstances";

  @Test
  void shouldReturnFilesPaths() {
    var request = new BulkUpsertRequest().withRecordsFileName(BULK_INSTANCES_FILE_PATH);
    var context = new BulkProcessingContext(request);

    assertEquals("parent-folder/bulkInstances_failedEntities", context.getErrorEntitiesFilePath());
    assertEquals("parent-folder/bulkInstances_errors", context.getErrorsFilePath());
    assertEquals(
      "mod-inventory-storage/parent-folder/bulkInstances_failedEntities", context.getErrorEntitiesFileLocalPath());
    assertEquals("mod-inventory-storage/parent-folder/bulkInstances_errors", context.getErrorsFileLocalPath());
  }
}
