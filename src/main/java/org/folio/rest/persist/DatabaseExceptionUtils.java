package org.folio.rest.persist;

import java.util.Map;

import com.github.mauricio.async.db.postgresql.exceptions.GenericDatabaseException;
import com.github.mauricio.async.db.postgresql.messages.backend.ErrorMessage;

import scala.collection.JavaConverters;

public class DatabaseExceptionUtils {
  private DatabaseExceptionUtils() {
    throw new UnsupportedOperationException("Cannot instantiate utility class.");
  }

  /**
   * If this throwable is an Exception thrown because of a foreign key violation then
   * return the message text and the detail text of that Exception, otherwise return null.
   *
   * @param throwable - where to read the text from
   * @return message and detail of the violation, or null if not a foreign key violation
   */
  public static String foreignKeyViolation(Throwable throwable) {
    if (!(throwable instanceof GenericDatabaseException)) {
      return null;
    }

    ErrorMessage errorMessage = ((GenericDatabaseException) throwable).errorMessage();
    Map<Object,String> fields = JavaConverters.mapAsJavaMapConverter(errorMessage.fields()).asJava();
    String sqlstate = fields.get('C');
    final String foreignKeyViolation = "23503";  // https://www.postgresql.org/docs/current/static/errcodes-appendix.html
    if (! foreignKeyViolation.equals(sqlstate)) {
      return null;
    }

    String detail = fields.get('D');
    String message = fields.get('M');
    return message + ": " + detail;
  }
}
