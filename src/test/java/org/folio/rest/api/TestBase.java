package org.folio.rest.api;

import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.rest.support.HttpClient;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import io.vertx.core.Vertx;

/**
 * When not run from StorageTestSuite then this class invokes StorageTestSuite.before() and
 * StorageTestSuite.after() to allow to run a single test class, for example from within an
 * IDE during development.
 */
@SuppressWarnings("squid:S1118")  // suppress "Utility classes should not have public constructors"
public class TestBase {
  private static boolean invokeStorageTestSuiteAfter = false;
  static HttpClient client;

  @BeforeClass
  public static void testBaseBeforeClass() throws Exception {
    Vertx vertx = StorageTestSuite.getVertx();
    if (vertx == null) {
      invokeStorageTestSuiteAfter = true;
      StorageTestSuite.before();
      vertx = StorageTestSuite.getVertx();
    }

    client = new HttpClient(vertx);

    StorageTestSuite.deleteAll();
  }

  @AfterClass
  public static void testBaseAfterClass() throws MalformedURLException, InterruptedException, ExecutionException, TimeoutException {
    if (invokeStorageTestSuiteAfter) {
      StorageTestSuite.after();
    }
  }
}
