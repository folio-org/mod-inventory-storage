package org.folio.rest.impl;

import io.vertx.core.http.HttpClient;
import java.util.List;
import java.util.UUID;
import org.folio.rest.jaxrs.model.Location;
import org.folio.rest.jaxrs.model.LocationCampus;
import org.folio.rest.jaxrs.model.LocationInstitution;
import org.folio.rest.jaxrs.model.LocationLibrary;
import org.folio.rest.jaxrs.model.ServicePoint;

/**
 * Creates location-unit records (institution/campus/library/service-point/location) for
 * {@code *IT} tests, rather than relying on shared class-level state (see docs/testing.md).
 * Each call creates a fresh record with a random id.
 */
final class LocationStorageFixtures {

  private LocationStorageFixtures() {
  }

  static String createInstitution(HttpClient client) {
    var id = UUID.randomUUID().toString();
    var institution = new LocationInstitution().withId(id).withName("test institution " + id)
      .withCode(id.substring(0, 8));

    BaseIntegrationTest.get(BaseIntegrationTest.doPost(
      client, ResourcePaths.LOCATION_UNITS_INSTITUTIONS, BaseIntegrationTest.pojo2JsonObject(institution)));

    return id;
  }

  static String createCampus(HttpClient client, String institutionId) {
    var id = UUID.randomUUID().toString();
    var campus = new LocationCampus().withId(id).withName("test campus " + id)
      .withCode(id.substring(0, 8)).withInstitutionId(institutionId);

    BaseIntegrationTest.get(BaseIntegrationTest.doPost(
      client, ResourcePaths.LOCATION_UNITS_CAMPUSES, BaseIntegrationTest.pojo2JsonObject(campus)));

    return id;
  }

  static String createLibrary(HttpClient client, String campusId) {
    var id = UUID.randomUUID().toString();
    var library = new LocationLibrary().withId(id).withName("test library " + id)
      .withCode(id.substring(0, 8)).withCampusId(campusId);

    BaseIntegrationTest.get(BaseIntegrationTest.doPost(
      client, ResourcePaths.LOCATION_UNITS_LIBRARIES, BaseIntegrationTest.pojo2JsonObject(library)));

    return id;
  }

  static String createServicePoint(HttpClient client) {
    var id = UUID.randomUUID().toString();
    var servicePoint = new ServicePoint().withId(id).withName("test service point " + id)
      .withCode(id.substring(0, 8)).withDiscoveryDisplayName("test service point " + id);

    BaseIntegrationTest.get(BaseIntegrationTest.doPost(
      client, ResourcePaths.SERVICE_POINTS, BaseIntegrationTest.pojo2JsonObject(servicePoint)));

    return id;
  }

  static String createLocation(HttpClient client, String institutionId, String campusId, String libraryId,
                                String servicePointId) {
    var id = UUID.randomUUID().toString();
    var location = new Location().withId(id).withName("test location " + id)
      .withCode(id.substring(0, 8)).withInstitutionId(institutionId).withCampusId(campusId)
      .withLibraryId(libraryId).withPrimaryServicePoint(UUID.fromString(servicePointId))
      .withServicePointIds(List.of(UUID.fromString(servicePointId)));

    BaseIntegrationTest.get(BaseIntegrationTest.doPost(
      client, ResourcePaths.LOCATIONS, BaseIntegrationTest.pojo2JsonObject(location)));

    return id;
  }

  /**
   * Builds a full institution/campus/library/service-point chain and a location on top of it,
   * for tests that only need a valid location id and don't care about the chain itself.
   */
  static String createLocation(HttpClient client) {
    var institutionId = createInstitution(client);
    var campusId = createCampus(client, institutionId);
    var libraryId = createLibrary(client, campusId);
    var servicePointId = createServicePoint(client);

    return createLocation(client, institutionId, campusId, libraryId, servicePointId);
  }
}
