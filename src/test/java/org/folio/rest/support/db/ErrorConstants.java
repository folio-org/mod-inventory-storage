package org.folio.rest.support.db;

public class ErrorConstants {
  public static final String NOT_NULL_VIOLATION_ERROR_CODE = "23502";
  public static final String FOREIGN_KEY_VIOLATION_ERROR_CODE = "23503";
  public static final String UNIQUE_VIOLATION_ERROR_CODE = "23505";
  public static final String CHECK_VIOLATION_ERROR_CODE = "23514";
  public static final String EXCLUSION_VIOLATION_ERROR_CODE = "23P01";
  public static final String INTEGRITY_VIOLATION__ERROR_CODE = "23506";
  public static final String INVALID_TEXT_REPRESENTATION_ERROR_CODE = "22P02";
  public static final String DATATYPE_MISMATCH_ERROR_CODE = "42804";
  public static final String DATA_LENGTH_MISMATCH_ERROR_CODE = "22001";
  public static final String INVALID_PASSWORD_ERROR_CODE = "28P01";

  public static final String SCHEMA_NAME = "fs_mod_notes";

  public static final String CHILD_TABLE_NAME = "child";
  public static final String PARENT_TABLE_NAME = "parent";

  public static final String ERROR_TYPE = "ERROR";
  public static final String FATAL_TYPE = "FATAL";
}
