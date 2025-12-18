package org.folio.services;

import static org.folio.utils.Environment.getEnvVar;

import org.apache.commons.lang3.StringUtils;
import org.folio.rest.jaxrs.model.BulkUpsertRequest;

/**
 * Encapsulates the context for bulk processing of entities from external file.
 * Holds the file paths necessary for bulk processing operation.
 */
public class BulkProcessingContext {

  private static final String DEFAULT_S3_LOCAL_SUB_PATH = "mod-inventory-storage";
  private static final String FAILED_ENTITIES_FILE_SUFFIX = "_failedEntities";
  private static final String ERRORS_FILE_SUFFIX = "_errors";

  private final String errorEntitiesFilePath;
  private final String errorsFilePath;
  private final String errorEntitiesFileLocalPath;
  private final String errorsFileLocalPath;
  private final boolean publishEvents;

  public BulkProcessingContext(BulkUpsertRequest request) {
    var s3LocalSubPath = "%s/".formatted(getEnvVar("S3_LOCAL_SUB_PATH", DEFAULT_S3_LOCAL_SUB_PATH));
    var initialFilePath =
      StringUtils.removeStart(request.getRecordsFileName(), '/');
    this.errorEntitiesFilePath = initialFilePath + FAILED_ENTITIES_FILE_SUFFIX;
    this.errorsFilePath = initialFilePath + ERRORS_FILE_SUFFIX;
    this.errorEntitiesFileLocalPath = s3LocalSubPath + errorEntitiesFilePath;
    this.errorsFileLocalPath = s3LocalSubPath + errorsFilePath;
    this.publishEvents = request.getPublishEvents();
  }

  public String getErrorEntitiesFilePath() {
    return errorEntitiesFilePath;
  }

  public String getErrorsFilePath() {
    return errorsFilePath;
  }

  public String getErrorEntitiesFileLocalPath() {
    return errorEntitiesFileLocalPath;
  }

  public String getErrorsFileLocalPath() {
    return errorsFileLocalPath;
  }

  public boolean isPublishEvents() {
    return publishEvents;
  }
}
