package org.folio.rest.persist;

import com.github.mauricio.async.db.exceptions.DatabaseException;
import com.github.mauricio.async.db.postgresql.exceptions.GenericDatabaseException;
import com.github.mauricio.async.db.postgresql.messages.backend.ErrorMessage;
import org.junit.Ignore;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

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
  @Ignore
  public void nullErrorMessage() {
    assertIsNull(null);
  }

  @Test
  @Ignore
  public void nullFields() {
    assertIsNull(new ErrorMessage(null));
  }

  @Test
  @Ignore
  public void noField() {
    scala.collection.immutable.Map<Object, String> map = new scala.collection.immutable.HashMap<Object, String>();
    assertIsNull(new ErrorMessage(map));
  }

  @Test
  public void utilityClass() {
    // UtilityClassTester.assertUtilityClass(DatabaseExceptionUtilsTest.class);
  }

  private void assertIsNull(ErrorMessage errorMessage) {
    assertThat(DatabaseExceptionUtils.badRequestMessage(new GenericDatabaseException(errorMessage)), is(nullValue()));
  }
}
