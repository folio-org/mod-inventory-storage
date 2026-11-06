package org.folio.persist.helper;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.folio.rest.exceptions.ValidationException;
import org.folio.rest.persist.PgExceptionFacade;
import org.folio.rest.tools.utils.ValidationHelper;

/**
 * Translates Postgres constraint violations into {@link ValidationException}, using the same keys and messages
 * as RMB's {@code PgUtil}, so the HTTP contract of the endpoints stays the same.
 */
public final class PgViolationMapper {

  public static final String INVALID_UUID = "Invalid UUID format of id, should be "
    + "xxxxxxxx-xxxx-Mxxx-Nxxx-xxxxxxxxxxxx where M is 1-5 and N is 8, 9, a, b, A or B and "
    + "x is 0-9, a-f or A-F.";

  @SuppressWarnings("java:S5852")  // linear runtime thanks to atomic groups (?>)
  private static final Pattern KEY_ALREADY_EXISTS_PATTERN = Pattern.compile(
    "^Key (?>\\((.+)\\)=\\()(.*)\\) already exists.$");
  @SuppressWarnings("java:S5852")  // linear runtime thanks to atomic groups (?>)
  private static final Pattern KEY_NOT_PRESENT_PATTERN = Pattern.compile(
    "^Key (?>(?>[(](.+)[)]=[(])(.*)[)]) is not present in table \"([^\"]+)\".$");
  @SuppressWarnings("java:S5852")  // linear runtime thanks to atomic groups (?>)
  private static final Pattern KEY_STILL_REFERENCED_PATTERN = Pattern.compile(
    "^Key (?>(?>[(](.+)[)]=[(])(.*)[)]) is still referenced from table \"([^\"]+)\".$");

  private PgViolationMapper() {
  }

  /**
   * Returns a {@link ValidationException} when the cause is a unique or foreign key violation,
   * otherwise returns the cause unchanged.
   */
  public static Throwable map(String table, String id, Throwable cause) {
    var pgException = new PgExceptionFacade(cause);
    if (pgException.isForeignKeyViolation()) {
      return foreignKeyViolation(table, id, pgException);
    }
    if (pgException.isUniqueViolation()) {
      return uniqueViolation(table, id, pgException);
    }
    return cause;
  }

  public static ValidationException invalidUuid(String table, String id) {
    return validation(table + ".id", id, INVALID_UUID);
  }

  private static ValidationException foreignKeyViolation(String table, String id, PgExceptionFacade pgException) {
    var detail = Objects.toString(pgException.getDetail(), "");
    var matcher = KEY_NOT_PRESENT_PATTERN.matcher(detail);
    if (matcher.find()) {
      return validation(table + "." + matcher.group(1), matcher.group(2),
        "Cannot set " + table + "." + matcher.group(1) + " = " + matcher.group(2)
          + " because it does not exist in " + matcher.group(3) + ".id.");
    }

    matcher = KEY_STILL_REFERENCED_PATTERN.matcher(detail);
    if (matcher.find()) {
      return validation(table + "." + matcher.group(1), matcher.group(2),
        "Cannot delete " + table + "." + matcher.group(1) + " = " + matcher.group(2)
          + " because id is still referenced from table " + matcher.group(3) + ".");
    }

    return validation(table, id, pgException.getMessage() + " " + detail);
  }

  private static ValidationException uniqueViolation(String table, String id, PgExceptionFacade pgException) {
    var detail = Objects.toString(pgException.getDetail(), "");
    Matcher matcher = KEY_ALREADY_EXISTS_PATTERN.matcher(detail);
    if (!matcher.find()) {
      return validation(table, id, pgException.getMessage() + " " + detail);
    }
    return validation(matcher.group(1), matcher.group(2),
      matcher.group(1) + " value already exists in table " + table + ": " + matcher.group(2));
  }

  private static ValidationException validation(String key, String value, String message) {
    return new ValidationException(ValidationHelper.createValidationErrorMessage(key, value, message));
  }
}
