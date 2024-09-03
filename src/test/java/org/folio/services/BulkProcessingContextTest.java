package org.folio.services;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BulkProcessingContextTest {

  private static final String BULK_INSTANCES_FILE_PATH = "/parent-folder/bulkInstances";

  @Test
  public void shouldReturnFilesPaths() {
    BulkProcessingContext context = new BulkProcessingContext(BULK_INSTANCES_FILE_PATH);

    assertEquals("parent-folder/bulkInstances_failedEntities", context.getErrorEntitiesFilePath());
    assertEquals("parent-folder/bulkInstances_errors", context.getErrorsFilePath());
    assertEquals("temp/parent-folder/bulkInstances_failedEntities", context.getErrorEntitiesFileLocalPath());
    assertEquals("temp/parent-folder/bulkInstances_errors", context.getErrorsFileLocalPath());
  }

}
