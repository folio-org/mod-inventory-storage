package org.folio.services;

import org.apache.commons.lang3.StringUtils;

/**
 * Encapsulates the context for bulk processing of entities from external file.
 * Holds the file paths necessary for bulk processing operation.
 */
public class BulkProcessingContext {

  private static final String ROOT_FOLDER = "temp/";
  private static final String FAILED_ENTITIES_FILE_SUFFIX = "_failedEntities";
  private static final String ERRORS_FILE_SUFFIX = "_errors";

  private final String initialFilePath;
  private final String errorEntitiesFilePath;
  private final String errorsFilePath;
  private final String errorEntitiesFileLocalPath;
  private final String errorsFileLocalPath;

  public BulkProcessingContext(String entitiesFilePath) {
    this.initialFilePath = StringUtils.removeStart(entitiesFilePath, '/');
    this.errorEntitiesFilePath = initialFilePath + FAILED_ENTITIES_FILE_SUFFIX;
    this.errorsFilePath = initialFilePath + ERRORS_FILE_SUFFIX;
    this.errorEntitiesFileLocalPath = ROOT_FOLDER + errorEntitiesFilePath;
    this.errorsFileLocalPath = ROOT_FOLDER + errorsFilePath;
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

}
