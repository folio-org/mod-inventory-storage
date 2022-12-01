package org.folio.utility;

import static org.folio.rest.api.TestBase.get;
import static org.folio.rest.support.http.InterfaceUrls.locCampusStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.locInstitutionStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.locLibraryStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.locationsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.servicePointsUrl;
import static org.folio.utility.RestUtility.send;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.folio.rest.jaxrs.model.HoldShelfExpiryPeriod;
import org.folio.rest.jaxrs.model.StaffSlip;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;

public class LocationUtility {
  private static final String SUPPORTED_CONTENT_TYPE_JSON_DEF = "application/json";
  private static UUID institutionID;
  private static UUID campusID;
  private static UUID libraryID;
  private static final List<UUID> servicePointIDs = new ArrayList<>();

  private LocationUtility() {
    throw new UnsupportedOperationException("Cannot instantiate utility class.");
  }

  public static void createLocationUnits(boolean force) {

    try {
      if (force || institutionID == null) {
        institutionID = UUID.randomUUID();
        createInstitution(institutionID, "Primary Institution", "PI");

        campusID = UUID.randomUUID();
        createCampus(campusID, "Central Campus", "CC", institutionID);

        libraryID = UUID.randomUUID();
        createLibrary(libraryID, "Main Library", "ML", campusID);

        UUID spID = UUID.randomUUID();
        servicePointIDs.add(spID);
        String spName = "Service Point " + spID.toString();
        createServicePoint(spID, spName, "SP" + spID.toString(), spName, "SP Description", 0, false, null);
      }
    } catch (Exception e) { // should not happen
      throw new AssertionError("CreateLocUnits failed:", e);
    }
  }

  public static Response createInstitution(UUID id, String name, String code) {
    CompletableFuture<Response> createLocationUnit = new CompletableFuture<>();

    JsonObject request = new JsonObject()
      .put("name", name)
      .put("code", code);
    if (id != null) {
      request.put("id", id.toString());
    }

    send(locInstitutionStorageUrl(""), HttpMethod.POST, "test_user", request.toString(),
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(createLocationUnit));

    return get(createLocationUnit);
  }

  public static Response createCampus(UUID id, String name, String code, UUID instId) {
    CompletableFuture<Response> createLocationUnit = new CompletableFuture<>();
    JsonObject request = new JsonObject()
      .put("name", name)
      .put("code", code);
    if (instId != null) { // should not be, except when testing it
      request.put("institutionId", instId.toString());
    }
    if (id != null) {
      request.put("id", id.toString());
    }

    send(locCampusStorageUrl(""), HttpMethod.POST, "test_user", request.toString(),
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(createLocationUnit));

    return get(createLocationUnit);
  }

  public static Response createLocation(UUID id, String name,
      UUID inst, UUID camp, UUID lib, String code, List<UUID> servicePoints) {

    CompletableFuture<Response> createLocation = new CompletableFuture<>();
    JsonObject request = new JsonObject()
      .put("name", name)
      .put("discoveryDisplayName", "d:" + name)
      .put("description", "something like " + name);
    putIfNotNull(request, "id", id);
    putIfNotNull(request, "institutionId", inst);
    putIfNotNull(request, "campusId", camp);
    putIfNotNull(request, "libraryId", lib);
    putIfNotNull(request, "code", code);
    putIfNotNull(request, "primaryServicePoint", servicePoints.get(0));
    putIfNotNull(request, "isActive", "true");
    UUID spID = UUID.randomUUID();
    servicePointIDs.add(spID);
    putIfNotNull(request, "servicePointIds", new JsonArray(servicePoints));
    send(locationsStorageUrl(""), HttpMethod.POST, "test_user", request.toString(),
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(createLocation));
    return get(createLocation);
  }

  /**
   * Helper to create a Location record the way old shelfLocations were created.
   * Used mostly while migrating to new Locations
   *
   * @param id
   * @param name
   * @param servicePoint
   * @param libID2
   * @param campID2
   * @param instID2
   * @return
   */
  public static UUID createLocation(UUID id, String name, String code) {
    if (id == null) {
      id = UUID.randomUUID();
    }
    createLocation(id, name, institutionID, campusID, libraryID, code, servicePointIDs);
    return id;
  }

  public static Response createLibrary(UUID id, String name, String code, UUID campId) {
    CompletableFuture<Response> createLocationUnit = new CompletableFuture<>();

    JsonObject request = new JsonObject()
      .put("name", name)
      .put("code", code)
      .put("campusId", campId.toString());
    if (id != null) {
      request.put("id", id.toString());
    }

    send(locLibraryStorageUrl(""), HttpMethod.POST, "test_user", request.toString(),
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(createLocationUnit));

    return get(createLocationUnit);
  }

  public static Response createServicePoint(UUID id, String name, String code,
      String discoveryDisplayName, String description, Integer shelvingLagTime,
      Boolean pickupLocation, HoldShelfExpiryPeriod shelfExpiryPeriod)
      throws MalformedURLException, InterruptedException, ExecutionException, TimeoutException {

    return createServicePoint(id, name, code, discoveryDisplayName, description,
      shelvingLagTime, pickupLocation, shelfExpiryPeriod, Collections.emptyList());
  }

  public static Response createServicePoint(UUID id, String name, String code,
      String discoveryDisplayName, String description, Integer shelvingLagTime,
      Boolean pickupLocation, HoldShelfExpiryPeriod shelfExpiryPeriod, List<StaffSlip> slips)
      throws MalformedURLException, InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture<Response> createServicePoint = new CompletableFuture<>();
    JsonObject request = new JsonObject();
    request
      .put("name", name)
      .put("code", code)
      .put("discoveryDisplayName", discoveryDisplayName);
    if(id != null) { request.put("id", id.toString()); }
    if(description != null) { request.put("description", description); }
    if(shelvingLagTime != null) { request.put("shelvingLagTime", shelvingLagTime); }
    if(pickupLocation != null) { request.put("pickupLocation", pickupLocation); }
    if(shelfExpiryPeriod != null) {request.put("holdShelfExpiryPeriod", new JsonObject(Json.encode(shelfExpiryPeriod)));}

    if (!slips.isEmpty()) {
      JsonArray staffSlips = new JsonArray();
      for (StaffSlip ss : slips) {
        JsonObject staffSlip = new JsonObject();
        staffSlip.put("id", ss.getId());
        staffSlip.put("printByDefault", ss.getPrintByDefault());
        staffSlips.add(staffSlip);
      }
      request.put("staffSlips", staffSlips);
    }

    send(servicePointsUrl(""), HttpMethod.POST, request.toString(),
        SUPPORTED_CONTENT_TYPE_JSON_DEF,
        ResponseHandler.json(createServicePoint));
    return createServicePoint.get(10, TimeUnit.SECONDS);
  }

  public static UUID getInstitutionID() {
    return institutionID;
  }

  public static UUID getCampusID() {
    return campusID;
  }

  public static UUID getLibraryID() {
    return libraryID;
  }

  public static List<UUID> getServicePointIDs() {
    return servicePointIDs;
  }

  public static void clearServicePointIDs() {
    servicePointIDs.clear();
  }

  private static void putIfNotNull(JsonObject js, String key, String value) {
    if (value != null) {
      js.put(key, value);
    }
  }

  private static void putIfNotNull(JsonObject js, String key, UUID value) {
    if (value != null) {
      js.put(key, value.toString());
    }
  }

  private static void putIfNotNull(JsonObject js, String key, JsonArray value) {
    if (value != null) {
      js.put(key, value);
    }
  }
}
