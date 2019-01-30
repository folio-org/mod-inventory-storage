/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.folio.rest.api;

import static org.folio.rest.api.TestBase.client;
import static org.folio.rest.support.http.InterfaceUrls.*;
import static org.junit.Assert.assertTrue;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.junit.Test;

/**
 *
 * @author ne
 */
public class ReferenceTablesTest extends TestBase {

  @Test
  public void alternativeTitleTypes()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {
    Response searchResponse = getReferenceRecords(alternativeTitleTypesUrl(""));
    validateNumberOfReferenceRecords("alternative title types", searchResponse, 5, 40);
  }

  @Test
  public void callNumberTypes()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {
    Response searchResponse = getReferenceRecords(callNumberTypesUrl(""));
    validateNumberOfReferenceRecords("call number types", searchResponse, 5, 40);
  }

  @Test
  public void classificationTypes()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {
    Response searchResponse = getReferenceRecords(classificationTypesUrl(""));
    validateNumberOfReferenceRecords("classification types", searchResponse, 2, 20);
  }

  @Test
  public void contributorNameTypes()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {
    Response searchResponse = getReferenceRecords(contributorNameTypesUrl(""));
    validateNumberOfReferenceRecords("contributor name types", searchResponse, 3, 10);
  }

  @Test
  public void contributorTypes()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {
    Response searchResponse = getReferenceRecords(contributorTypesUrl(""));
    validateNumberOfReferenceRecords("contributor types", searchResponse, 20, 500);
  }

  @Test
  public void electronicAccessRelationships()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {
    Response searchResponse = getReferenceRecords(electronicAccessRelationshipsUrl(""));
    validateNumberOfReferenceRecords("electronic access relationship types", searchResponse, 2, 20);
  }

  @Test
  public void holdingsNoteTypes()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {
    Response searchResponse = getReferenceRecords(holdingsNoteTypesUrl(""));
    validateNumberOfReferenceRecords("holdings note types", searchResponse, 5, 20);
  }

  @Test
  public void holdingsTypes()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {
    Response searchResponse = getReferenceRecords(holdingsTypesUrl(""));
    validateNumberOfReferenceRecords("holdings types", searchResponse, 3, 20);
  }

  @Test
  public void identifierTypes()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {
    Response searchResponse = getReferenceRecords(identifierTypesUrl(""));
    validateNumberOfReferenceRecords("identifier types", searchResponse, 8, 30);
  }

  @Test
  public void illPolicies()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {
    Response searchResponse = getReferenceRecords(illPoliciesUrl(""));
    validateNumberOfReferenceRecords("ILL policies", searchResponse, 5, 20);
  }

  @Test
  public void instanceFormats()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {
    Response searchResponse = getReferenceRecords(instanceFormatsUrl(""));
    validateNumberOfReferenceRecords("instance formats", searchResponse, 20, 200);
  }

  @Test
  public void instanceStatuses()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {
    Response searchResponse = getReferenceRecords(instanceStatusesUrl(""));
    validateNumberOfReferenceRecords("instance statuses", searchResponse, 5, 20);
  }

  @Test
  public void instanceTypes()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {
    Response searchResponse = getReferenceRecords(instanceTypesUrl(""));
    validateNumberOfReferenceRecords("instance types (resource types)", searchResponse, 10, 100);
  }

  @Test
  public void itemNoteTypes()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {
    Response searchResponse = getReferenceRecords(itemNoteTypesUrl(""));
    validateNumberOfReferenceRecords("item note types", searchResponse, 5, 20);
  }

  @Test
  public void modesOfIssuance()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {
    Response searchResponse = getReferenceRecords(modesOfIssuanceUrl(""));
    validateNumberOfReferenceRecords("modes of issuance", searchResponse, 4, 10);
  }

  @Test
  public void statisticalCodes()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {
    Response searchResponse = getReferenceRecords(statisticalCodesUrl(""));
    validateNumberOfReferenceRecords("statistical codes", searchResponse, 10, 500);
  }

  @Test
  public void statisticalCodeTypes()
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {
    Response searchResponse = getReferenceRecords(statisticalCodeTypesUrl(""));
    validateNumberOfReferenceRecords("statistical code types", searchResponse, 2, 50);
  }

  private Response getReferenceRecords(URL baseUrl)
          throws InterruptedException,
          MalformedURLException,
          TimeoutException,
          ExecutionException,
          UnsupportedEncodingException {

    CompletableFuture<Response> searchCompleted = new CompletableFuture<>();
    String url = baseUrl.toString() + "?limit=400&query="
            + URLEncoder.encode("cql.allRecords=1", StandardCharsets.UTF_8.name());
    client.get(url, StorageTestSuite.TENANT_ID, ResponseHandler.json(searchCompleted));
    Response searchResponse = searchCompleted.get(5, TimeUnit.SECONDS);
    return searchResponse;
  }

  private void validateNumberOfReferenceRecords(String dataDescription, Response searchResponse, int min, int max) {

    Integer totalRecords = searchResponse.getJson().getInteger("totalRecords");
    assertTrue(String.format("Could not retrieve record count for %s", dataDescription), totalRecords != null);
    assertTrue(String.format("Expected <=%s \"%s\", found %s", max, dataDescription, totalRecords), max >= totalRecords);
    assertTrue(String.format("Expected >=%s \"%s\", found %s", min, dataDescription, totalRecords), min <= totalRecords);

  }
}
