package org.folio.rest.persist;

import com.github.mauricio.async.db.exceptions.DatabaseException;
import com.github.mauricio.async.db.postgresql.exceptions.GenericDatabaseException;
import com.github.mauricio.async.db.postgresql.messages.backend.ErrorMessage;

import scala.Predef;
import scala.Tuple2;
import scala.collection.JavaConverters;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

//import org.folio.rest.tools.utils.UtilityClassTester;

public class DatabaseExceptionUtilsTest {
  @Test
  public void nullThrowable() {
    assertThat(DatabaseExceptionUtils.badRequestMessage(null), is(nullValue()));
  }

  @Test
  public void throwable() {
    assertThat(DatabaseExceptionUtils.badRequestMessage(new Throwable()), is(nullValue()));
  }

  @Test
  public void databaseException() {
    assertThat(DatabaseExceptionUtils.badRequestMessage(new DatabaseException("")), is(nullValue()));
  }

  @Test
  public void noField() {
    @SuppressWarnings("unchecked")
    ErrorMessage m = new ErrorMessage(scalaMap(Collections.EMPTY_MAP));
    assertThat(DatabaseExceptionUtils.badRequestMessage(new GenericDatabaseException(m)), is(nullValue()));
  }

  @Test
  public void foreignKeyViolation() {
    assertString("23503", "some detail", "the message", "the message: some detail");
  }

  @Test
  public void invalidTextRepresentation() {
    assertString("22P02", "detail", "message", "message");
  }

  @Test
  public void utilityClass() {
    // UtilityClassTester.assertUtilityClass(DatabaseExceptionUtilsTest.class);
  }

  private void assertString(String sqlstate, String detail, String message, String expected) {
    Map<Object, String> javaMap = new HashMap<>();
    javaMap.put('C', sqlstate);
    javaMap.put('D', detail);
    javaMap.put('M', message);
    ErrorMessage errorMessage = new ErrorMessage(scalaMap(javaMap));
    String actual = DatabaseExceptionUtils.badRequestMessage(new GenericDatabaseException(errorMessage));
    assertThat(actual, is(expected));
  }

  /**
   * @return javaMap as an immutable scala map
   */
  private scala.collection.immutable.Map<Object, String> scalaMap(Map<Object, String> javaMap) {
    return JavaConverters.mapAsScalaMapConverter(javaMap).asScala().toMap(Predef.<Tuple2<Object, String>>conforms());
  }
}
