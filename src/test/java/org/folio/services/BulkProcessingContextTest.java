package org.folio.services;

import static org.junit.Assert.assertEquals;

import org.folio.rest.jaxrs.model.BulkUpsertRequest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BulkProcessingContextTest {

  private static final String BULK_INSTANCES_FILE_PATH = "/parent-folder/bulkInstances";

  @Test
  public void shouldReturnFilesPaths() {
    var request = new BulkUpsertRequest().withRecordsFileName(BULK_INSTANCES_FILE_PATH);
    var context = new BulkProcessingContext(request);

    assertEquals("parent-folder/bulkInstances_failedEntities", context.getErrorEntitiesFilePath());
    assertEquals("parent-folder/bulkInstances_errors", context.getErrorsFilePath());
    assertEquals("temp/parent-folder/bulkInstances_failedEntities", context.getErrorEntitiesFileLocalPath());
    assertEquals("temp/parent-folder/bulkInstances_errors", context.getErrorsFileLocalPath());
  }

}
