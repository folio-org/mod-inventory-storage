package org.folio.rest.api;

import static org.folio.rest.support.http.InterfaceUrls.contributorTypesUrl;
import static org.folio.util.StringUtil.urlEncode;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.junit.Test;

public class ContributorTypesTest extends TestBase {
  //See https://issues.folio.org/browse/MODINVSTOR-164 for context
  @Test
  public void canSearchForMoreThanOneContributorTypes()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException,
    UnsupportedEncodingException {

    CompletableFuture<Response> searchCompleted = new CompletableFuture<Response>();

    String url = contributorTypesUrl("").toString() + "?limit=400&query="
      + urlEncode("cql.allRecords=1");

    client.get(url, StorageTestSuite.TENANT_ID, ResponseHandler.json(searchCompleted));
    Response searchResponse = searchCompleted.get(5, TimeUnit.SECONDS);

    assertThat(
      String.format("Failed to search for instances: '%s'", searchResponse.getBody()),
      searchResponse.getStatusCode(), is(200));
  }
}
