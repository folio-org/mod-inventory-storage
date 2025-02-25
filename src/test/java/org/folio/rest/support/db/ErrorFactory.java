package org.folio.rest.support.db;

import static org.folio.rest.support.db.ErrorConstants.CHECK_VIOLATION_ERROR_CODE;
import static org.folio.rest.support.db.ErrorConstants.CHILD_TABLE_NAME;
import static org.folio.rest.support.db.ErrorConstants.DATATYPE_MISMATCH_ERROR_CODE;
import static org.folio.rest.support.db.ErrorConstants.DATA_LENGTH_MISMATCH_ERROR_CODE;
import static org.folio.rest.support.db.ErrorConstants.ERROR_TYPE;
import static org.folio.rest.support.db.ErrorConstants.EXCLUSION_VIOLATION_ERROR_CODE;
import static org.folio.rest.support.db.ErrorConstants.FATAL_TYPE;
import static org.folio.rest.support.db.ErrorConstants.FOREIGN_KEY_VIOLATION_ERROR_CODE;
import static org.folio.rest.support.db.ErrorConstants.INTEGRITY_VIOLATION_ERROR_CODE;
import static org.folio.rest.support.db.ErrorConstants.INVALID_PASSWORD_ERROR_CODE;
import static org.folio.rest.support.db.ErrorConstants.INVALID_TEXT_REPRESENTATION_ERROR_CODE;
import static org.folio.rest.support.db.ErrorConstants.NOT_NULL_VIOLATION_ERROR_CODE;
import static org.folio.rest.support.db.ErrorConstants.PARENT_TABLE_NAME;
import static org.folio.rest.support.db.ErrorConstants.SCHEMA_NAME;
import static org.folio.rest.support.db.ErrorConstants.UNIQUE_VIOLATION_ERROR_CODE;

import java.util.Map;

public final class ErrorFactory {

  private ErrorFactory() {
  }

  public static Map<Character, String> getForeignKeyErrorMap() {
    return new ErrorBuilder()
      .setMessage("insert or update on table \"child\" violates foreign key constraint \"fk_parent\"")
      .setDetail("Key (parent_id1, parent_id2)=(22222, 813205855) is not a present in table \"parent\"")
      .setSchema(SCHEMA_NAME)
      .setTable(CHILD_TABLE_NAME)
      .setFieldName("fk_parent")
      .setLine("3321")
      .setFile("ri.triggers.c")
      .setSqlState(FOREIGN_KEY_VIOLATION_ERROR_CODE)
      .setRoutine("ri_ReportViolation")
      .setErrorType(ERROR_TYPE)
      .setSeverity(ERROR_TYPE).build();
  }

  public static Map<Character, String> getPrimaryKeyErrorMap() {
    return new ErrorBuilder()
      .setMessage("duplicate key value violates unique constraint \"pk_parent\"")
      .setDetail("Key (id1, id2)=(22222, 813205855) already exists")
      .setSchema(SCHEMA_NAME)
      .setTable(PARENT_TABLE_NAME)
      .setFieldName("pk_parent")
      .setLine("434")
      .setFile("nbtinsert.c")
      .setSqlState(UNIQUE_VIOLATION_ERROR_CODE)
      .setRoutine("_bt_check_unique")
      .setErrorType(ERROR_TYPE)
      .setSeverity(ERROR_TYPE).build();
  }

  public static Map<Character, String> getUuidErrorMap() {
    return new ErrorBuilder()
      .setMessage("invalid input syntax for type uuid: \"INVALID\"")
      .setLine("137")
      .setFile("uuid.c")
      .setSqlState(INVALID_TEXT_REPRESENTATION_ERROR_CODE)
      .setRoutine("string_to_uuid")
      .setErrorType(ERROR_TYPE)
      .setSeverity(ERROR_TYPE).build();
  }

  public static Map<Character, String> getNotNullViolationErrorMap() {
    return new ErrorBuilder()
      .setMessage("null value in column \"name\" violates not-null constraint")
      .setDetail("Failing row constraints (1697635108, 858317485, null, 4670207833.23)")
      .setSchema(SCHEMA_NAME)
      .setTable(PARENT_TABLE_NAME)
      .setLine("2008")
      .setFile("execMain.c")
      .setFieldColumn("name")
      .setSqlState(NOT_NULL_VIOLATION_ERROR_CODE)
      .setRoutine("ExecConstraints")
      .setErrorType(ERROR_TYPE)
      .setSeverity(ERROR_TYPE).build();
  }

  public static Map<Character, String> getUniqueViolationErrorMap() {
    return new ErrorBuilder()
      .setMessage("duplicate key value violates unique constraint \"unq_name\"")
      .setDetail("Key (name)=(eOMtThyhVNLWUZNRcBaQKxl) already exists")
      .setSchema(SCHEMA_NAME)
      .setTable(PARENT_TABLE_NAME)
      .setLine("434")
      .setFile("nbtinsert.c")
      .setFieldColumn("name")
      .setSqlState(UNIQUE_VIOLATION_ERROR_CODE)
      .setRoutine("_bt_check_unique")
      .setErrorType(ERROR_TYPE)
      .setSeverity(ERROR_TYPE).build();
  }

  public static Map<Character, String> getCheckViolationErrorMap() {
    return new ErrorBuilder()
      .setMessage("new ow for relation \"parent\" violates check constraint")
      .setDetail("Failing row contains (1704747953, 1372598141, eOMtThyhVNLWUZNRcBaQKxl, -1.00)")
      .setSchema(SCHEMA_NAME)
      .setTable(PARENT_TABLE_NAME)
      .setLine("2055")
      .setFile("execMain.c")
      .setFieldName("positive_value")
      .setSqlState(CHECK_VIOLATION_ERROR_CODE)
      .setRoutine("ExecConstraints")
      .setErrorType(ERROR_TYPE)
      .setSeverity(ERROR_TYPE).build();
  }

  public static Map<Character, String> getIntegrityViolationErrorMap() {
    return new ErrorBuilder()
      .setMessage("new row for relation \"parent\" violates check constraint")
      .setDetail("Failing row contains (1704747953, 1372598141, eOMtThyhVNLWUZNRcBaQKxl, -1.00)")
      .setSchema(SCHEMA_NAME)
      .setTable(PARENT_TABLE_NAME)
      .setLine("2055")
      .setFile("execMain.c")
      .setFieldName("positive_value")
      .setSqlState(INTEGRITY_VIOLATION_ERROR_CODE)
      .setRoutine("ExecConstraints")
      .setErrorType(ERROR_TYPE)
      .setSeverity(ERROR_TYPE).build();
  }

  public static Map<Character, String> getExclusionViolationErrorMap() {
    return new ErrorBuilder()
      .setMessage("conflicting key value violates exclusion constraint \"exclude_overlapping_bookings\"")
      .setDetail("Key (daterange(from_date, to_date, '[]'::text))=([2017-04-20,2017-05-01)) "
        + "conflicts with existing key (daterange(from_date, to_date, '[]'::text))=([2017-04-20,2017-04-22))")
      .setSchema(SCHEMA_NAME)
      .setTable("booking")
      .setLine("839")
      .setFile("execIndexing.c")
      .setFieldName("exclude_overlapping_bookings")
      .setSqlState(EXCLUSION_VIOLATION_ERROR_CODE)
      .setRoutine("check_exclusion_or_unique_constraint")
      .setErrorType(ERROR_TYPE)
      .setSeverity(ERROR_TYPE).build();
  }

  public static Map<Character, String> getDataTypeMismatchViolation() {
    return new ErrorBuilder()
      .setMessage("column \"addresses\" is of type json but expression is of type character varying")
      .setSchema(SCHEMA_NAME)
      .setTable(PARENT_TABLE_NAME)
      .setLine("510")
      .setFile("parse_target.c")
      .setSqlState(DATATYPE_MISMATCH_ERROR_CODE)
      .setRoutine("transformAssignedExpr")
      .setErrorType(ERROR_TYPE)
      .setSeverity(ERROR_TYPE).build();
  }

  public static Map<Character, String> getDataLengthMismatch() {
    return new ErrorBuilder()
      .setMessage("value too long for type character varying(10)")
      .setLine("624")
      .setFile("varchar.c")
      .setSqlState(DATA_LENGTH_MISMATCH_ERROR_CODE)
      .setRoutine("varchar")
      .setErrorType(ERROR_TYPE)
      .setSeverity(ERROR_TYPE).build();
  }

  public static Map<Character, String> getInvalidPasswordErrorMap() {
    return new ErrorBuilder()
      .setMessage("password authentication failed for user \"wrong_mod_notes\"")
      .setLine("328")
      .setFile("auth.c")
      .setSqlState(INVALID_PASSWORD_ERROR_CODE)
      .setRoutine("auth_failed")
      .setErrorType(FATAL_TYPE)
      .setSeverity(FATAL_TYPE).build();
  }

  public static Map<Character, String> getErrorMapWithFieldNameOnly(String name) {
    return new ErrorBuilder().setFieldName(name).build();
  }

  public static Map<Character, String> getErrorMapWithFieldNameNull() {
    return new ErrorBuilder().setFieldName(null).build();
  }

  public static Map<Character, String> getErrorMapWithDetailOnly(String detail) {
    return new ErrorBuilder().setDetail(detail).build();
  }

  public static Map<Character, String> getErrorMapWithDetailNull() {
    return new ErrorBuilder().setDetail(null).build();
  }

  public static Map<Character, String> getErrorMapWithSqlStateOnly(String sqlState) {
    return new ErrorBuilder().setSqlState(sqlState).build();
  }

  public static Map<Character, String> getErrorMapWithSqlStateNull() {
    return new ErrorBuilder().setSqlState(null).build();
  }

  public static Map<Character, String> getErrorMapWithPsql(String psql) {
    return new ErrorBuilder().setSqlState(psql).build();
  }

  public static Map<Character, String> getErrorMapWithPsqlStateNull() {
    return new ErrorBuilder().setSqlState(null).build();
  }

  public static Map<Character, String> getErrorMapWithSchema(String schema) {
    return new ErrorBuilder().setSchema(schema).build();
  }

  public static Map<Character, String> getErrorMapWithSchemaNull() {
    return new ErrorBuilder().setSchema(null).build();
  }

  public static Map<Character, String> getErrorMapWithTable(String table) {
    return new ErrorBuilder().setTable(table).build();
  }

  public static Map<Character, String> getErrorMapWithTableNull() {
    return new ErrorBuilder().setTable(null).build();
  }

  public static Map<Character, String> getErrorMapWithMessage(String message) {
    return new ErrorBuilder().setMessage(message).build();
  }

  public static Map<Character, String> getErrorMapWithMessageNull() {
    return new ErrorBuilder().setMessage(null).build();
  }

  public static Map<Character, String> getErrorMapWithColumn(String column) {
    return new ErrorBuilder().setFieldColumn(column).build();
  }

  public static Map<Character, String> getErrorMapWithColumnNull() {
    return new ErrorBuilder().setMessage(null).build();
  }
}
