package org.folio.rest.persist;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
//import org.folio.rest.tools.utils.UtilityClassTester;

import org.junit.Test;

import com.github.mauricio.async.db.exceptions.DatabaseException;
import com.github.mauricio.async.db.postgresql.exceptions.GenericDatabaseException;
import com.github.mauricio.async.db.postgresql.messages.backend.ErrorMessage;

public class DatabaseExceptionUtilsTest {
  @Test
  public void nullThrowable() {
    assertThat(DatabaseExceptionUtils.foreignKeyViolation(null), is(nullValue()));
  }

  @Test
  public void throwable() {
    assertThat(DatabaseExceptionUtils.foreignKeyViolation(new Throwable()), is(nullValue()));
  }

  @Test
  public void databaseException() {
    assertThat(DatabaseExceptionUtils.foreignKeyViolation(new DatabaseException("")), is(nullValue()));
  }

  private void assertIsNull(ErrorMessage errorMessage) {
    assertThat(DatabaseExceptionUtils.foreignKeyViolation(new GenericDatabaseException(errorMessage)), is(nullValue()));
  }

  @Test
  public void nullErrorMessage() {
    assertIsNull(null);
  }

  @Test
  public void nullFields() {
    assertIsNull(new ErrorMessage(null));
  }

  @Test
  public void noField() {
    scala.collection.immutable.Map<Object, String> map = new scala.collection.immutable.HashMap<Object, String>();
    assertIsNull(new ErrorMessage(map));
  }

  @Test
  public void utilityClass() {
    // UtilityClassTester.assertUtilityClass(DatabaseExceptionUtilsTest.class);
  }
}
