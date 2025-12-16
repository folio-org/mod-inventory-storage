package org.folio.rest.support.db;

import java.util.HashMap;
import java.util.Map;

public class ErrorBuilder {

  private String message;
  private String detail;
  private String sqlState;
  private String schema;
  private String table;
  private String fieldName;
  private String fieldColumn;
  private String line;
  private String file;
  private String routine;
  private String severity;
  private String errorType;

  public ErrorBuilder setMessage(String message) {
    this.message = message;
    return this;
  }

  public ErrorBuilder setDetail(String detail) {
    this.detail = detail;
    return this;
  }

  public ErrorBuilder setSqlState(String sqlState) {
    this.sqlState = sqlState;
    return this;
  }

  public ErrorBuilder setSchema(String schema) {
    this.schema = schema;
    return this;
  }

  public ErrorBuilder setTable(String table) {
    this.table = table;
    return this;
  }

  public ErrorBuilder setFieldName(String fieldName) {
    this.fieldName = fieldName;
    return this;
  }

  public ErrorBuilder setFieldColumn(String fieldColumn) {
    this.fieldColumn = fieldColumn;
    return this;
  }

  public ErrorBuilder setLine(String line) {
    this.line = line;
    return this;
  }

  public ErrorBuilder setFile(String file) {
    this.file = file;
    return this;
  }

  public ErrorBuilder setRoutine(String routine) {
    this.routine = routine;
    return this;
  }

  public ErrorBuilder setSeverity(String severity) {
    this.severity = severity;
    return this;
  }

  public ErrorBuilder setErrorType(String errorType) {
    this.errorType = errorType;
    return this;
  }

  public Map<Character, String> build() {
    Map<Character, String> map = new HashMap<>();
    map.computeIfAbsent('D', val -> detail);
    map.computeIfAbsent('s', val -> schema);
    map.computeIfAbsent('n', val -> fieldName);
    map.computeIfAbsent('t', val -> table);
    map.computeIfAbsent('L', val -> line);
    map.computeIfAbsent('F', val -> file);
    map.computeIfAbsent('C', val -> sqlState);
    map.computeIfAbsent('c', val -> fieldColumn);
    map.computeIfAbsent('R', val -> routine);
    map.computeIfAbsent('V', val -> errorType);
    map.computeIfAbsent('M', val -> message);
    map.computeIfAbsent('S', val -> severity);

    return map;
  }
}
