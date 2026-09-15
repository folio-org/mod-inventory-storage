package org.folio.it;

import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;

import io.vertx.core.http.HttpClient;
import java.util.List;
import java.util.UUID;
import org.folio.rest.jaxrs.model.Location;
import org.folio.rest.jaxrs.model.LocationCampus;
import org.folio.rest.jaxrs.model.LocationInstitution;
import org.folio.rest.jaxrs.model.LocationLibrary;
import org.folio.rest.jaxrs.model.ServicePoint;
import org.folio.support.ResourcePaths;

/**
 * Creates location-unit records (institution/campus/library/service-point/location) for
 * {@code *IT} tests, rather than relying on shared class-level state (see docs/testing.md).
 * Each call creates a fresh record with a random id.
 */
public final class LocationStorageFixtures {

  private LocationStorageFixtures() {
  }

  public static String createInstitution(HttpClient client) {
    var id = UUID.randomUUID().toString();
    var institution = new LocationInstitution().withId(id).withName("test institution " + id)
      .withCode(id.substring(0, 8));

    await(BaseIntegrationTest.doPost(
      client, ResourcePaths.LOCATION_UNITS_INSTITUTIONS, BaseIntegrationTest.pojo2JsonObject(institution)));

    return id;
  }

  public static String createCampus(HttpClient client, String institutionId) {
    var id = UUID.randomUUID().toString();
    var campus = new LocationCampus().withId(id).withName("test campus " + id)
      .withCode(id.substring(0, 8)).withInstitutionId(institutionId);

    await(BaseIntegrationTest.doPost(
      client, ResourcePaths.LOCATION_UNITS_CAMPUSES, BaseIntegrationTest.pojo2JsonObject(campus)));

    return id;
  }

  public static String createLibrary(HttpClient client, String campusId) {
    var id = UUID.randomUUID().toString();
    var library = new LocationLibrary().withId(id).withName("test library " + id)
      .withCode(id.substring(0, 8)).withCampusId(campusId);

    await(BaseIntegrationTest.doPost(
      client, ResourcePaths.LOCATION_UNITS_LIBRARIES, BaseIntegrationTest.pojo2JsonObject(library)));

    return id;
  }

  public static String createServicePoint(HttpClient client) {
    var id = UUID.randomUUID().toString();
    var servicePoint = new ServicePoint().withId(id).withName("test service point " + id)
      .withCode(id.substring(0, 8)).withDiscoveryDisplayName("test service point " + id);

    await(BaseIntegrationTest.doPost(
      client, ResourcePaths.SERVICE_POINTS, BaseIntegrationTest.pojo2JsonObject(servicePoint)));

    return id;
  }

  public static String createLocation(HttpClient client, String institutionId, String campusId, String libraryId,
                                String servicePointId) {
    return createLocation(client, "test location " + UUID.randomUUID(),
      institutionId, campusId, libraryId, servicePointId);
  }

  /**
   * Same as {@link #createLocation(HttpClient, String, String, String, String)}, but with an
   * explicit name, for tests that assert on the location name itself rather than just needing
   * a valid id.
   */
  public static String createLocation(HttpClient client, String name, String institutionId, String campusId,
                                String libraryId, String servicePointId) {
    var id = UUID.randomUUID().toString();
    var location = new Location().withId(id).withName(name)
      .withCode(id.substring(0, 8)).withInstitutionId(institutionId).withCampusId(campusId)
      .withLibraryId(libraryId).withPrimaryServicePoint(UUID.fromString(servicePointId))
      .withServicePointIds(List.of(UUID.fromString(servicePointId))).withIsActive(true);

    await(BaseIntegrationTest.doPost(
      client, ResourcePaths.LOCATIONS, BaseIntegrationTest.pojo2JsonObject(location)));

    return id;
  }

  /**
   * Builds a full institution/campus/library/service-point chain and a location on top of it,
   * for tests that only need a valid location id and don't care about the chain itself.
   */
  public static String createLocation(HttpClient client) {
    return createLocation(client, "test location " + UUID.randomUUID());
  }

  /**
   * Same as {@link #createLocation(HttpClient)}, but with an explicit name, for tests that
   * assert on the location name itself rather than just needing a valid id.
   */
  public static String createLocation(HttpClient client, String name) {
    var institutionId = createInstitution(client);
    var campusId = createCampus(client, institutionId);
    var libraryId = createLibrary(client, campusId);
    var servicePointId = createServicePoint(client);

    return createLocation(client, name, institutionId, campusId, libraryId, servicePointId);
  }
}
