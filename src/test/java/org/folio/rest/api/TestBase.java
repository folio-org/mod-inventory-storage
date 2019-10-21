package org.folio.rest.api;

import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.rest.support.HttpClient;
import org.folio.rest.support.http.ResourceClient;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import io.vertx.core.Vertx;

/**
 * When not run from StorageTestSuite then this class invokes StorageTestSuite.before() and
 * StorageTestSuite.after() to allow to run a single test class, for example from within an
 * IDE during development.
 */
public abstract class TestBase {
  private static boolean invokeStorageTestSuiteAfter = false;
  static HttpClient client;
  static ResourceClient instancesClient;
  static ResourceClient holdingsClient;
  static ResourceClient itemsClient;
  static ResourceClient locationsClient;

  @BeforeClass
  public static void testBaseBeforeClass() throws Exception {
    Vertx vertx = StorageTestSuite.getVertx();
    if (vertx == null) {
      invokeStorageTestSuiteAfter = true;
      StorageTestSuite.before();
      vertx = StorageTestSuite.getVertx();
    }

    client = new HttpClient(vertx);
    instancesClient = ResourceClient.forInstances(client);
    holdingsClient = ResourceClient.forHoldings(client);
    itemsClient = ResourceClient.forItems(client);
    locationsClient = ResourceClient.forLocations(client);
  }

  @AfterClass
  public static void testBaseAfterClass()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    if (invokeStorageTestSuiteAfter) {
      StorageTestSuite.after();
    }
  }

}
