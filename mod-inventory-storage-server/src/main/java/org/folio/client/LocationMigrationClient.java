package org.folio.client;

import static org.folio.HttpStatus.HTTP_NO_CONTENT;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.LocationCampuses;
import org.folio.rest.jaxrs.model.LocationInstitutions;
import org.folio.rest.jaxrs.model.LocationLibraries;
import org.folio.rest.jaxrs.model.Locations;
import org.folio.rest.jaxrs.model.ServicePoints;
import org.folio.rest.jaxrs.model.ServicePointsUsers;

public final class LocationMigrationClient {

  private static final Logger log = LogManager.getLogger();

  private static final String MIGRATE_INSTITUTIONS_PATH = "/migrate/institutions";
  private static final String MIGRATE_CAMPUSES_PATH = "/migrate/campuses";
  private static final String MIGRATE_LIBRARIES_PATH = "/migrate/libraries";
  private static final String MIGRATE_LOCATIONS_PATH = "/migrate/locations";
  private static final String MIGRATE_SERVICE_POINTS_PATH = "/migrate/service-points";
  private static final String MIGRATE_SERVICE_POINTS_USERS_PATH = "/migrate/service-points-users";

  private final RestClient restClient;
  private final Map<String, String> headers;

  private LocationMigrationClient(Vertx vertx, Map<String, String> headers) {
    this.restClient = new RestClientImpl(vertx.createHttpClient());
    this.headers = headers;
  }

  public static Builder builder(Vertx vertx, Map<String, String> headers) {
    return new Builder(vertx, headers);
  }

  private Future<Void> migrateInstitutions(LocationInstitutions entities) {
    log.info("Start migration of institutions");
    return doPost(MIGRATE_INSTITUTIONS_PATH, entities.getLocinsts())
      .onSuccess(v -> log.info("Migration of institutions completed"));
  }

  private Future<Void> migrateCampuses(LocationCampuses entities) {
    log.info("Start migration of campuses");
    return doPost(MIGRATE_CAMPUSES_PATH, entities.getLoccamps())
      .onSuccess(v -> log.info("Migration of campuses completed"));
  }

  private Future<Void> migrateLibraries(LocationLibraries entities) {
    log.info("Start migration of libraries");
    return doPost(MIGRATE_LIBRARIES_PATH, entities.getLoclibs())
      .onSuccess(v -> log.info("Migration of libraries completed"));
  }

  private Future<Void> migrateLocations(Locations entities) {
    log.info("Start migration of locations");
    return doPost(MIGRATE_LOCATIONS_PATH, entities.getLocations())
      .onSuccess(v -> log.info("Migration of locations completed"));
  }

  private Future<Void> migrateServicePoints(ServicePoints entities) {
    log.info("Start migration of service points");
    return doPost(MIGRATE_SERVICE_POINTS_PATH, entities.getServicepoints())
      .onSuccess(v -> log.info("Migration of service points completed"));
  }

  private Future<Void> migrateServicePointsUsers(ServicePointsUsers entities) {
    log.info("Start migration of service points users");
    return doPost(MIGRATE_SERVICE_POINTS_USERS_PATH, entities.getServicePointsUsers())
      .onSuccess(v -> log.info("Migration of service points users completed"));
  }

  private <T> Future<Void> doPost(String path, List<T> entities) {
    if (entities == null || entities.isEmpty()) {
      return Future.succeededFuture();
    }
    return restClient.post(path, headers, entities, HTTP_NO_CONTENT.toInt()).mapEmpty();
  }

  public static final class Builder {

    private final LocationMigrationClient client;
    private ServicePoints servicePoints;
    private LocationInstitutions institutions;
    private LocationCampuses campuses;
    private LocationLibraries libraries;
    private Locations locations;
    private ServicePointsUsers servicePointsUsers;

    private Builder(Vertx vertx, Map<String, String> headers) {
      this.client = new LocationMigrationClient(vertx, headers);
    }

    public Builder servicePoints(ServicePoints servicePoints) {
      this.servicePoints = servicePoints;
      return this;
    }

    public Builder servicePointsUsers(ServicePointsUsers servicePointsUsers) {
      this.servicePointsUsers = servicePointsUsers;
      return this;
    }

    public Builder institutions(LocationInstitutions institutions) {
      this.institutions = institutions;
      return this;
    }

    public Builder campuses(LocationCampuses campuses) {
      this.campuses = campuses;
      return this;
    }

    public Builder libraries(LocationLibraries libraries) {
      this.libraries = libraries;
      return this;
    }

    public Builder locations(Locations locations) {
      this.locations = locations;
      return this;
    }

    public Future<Void> migrate() {
      return runIfPresent(servicePoints, () -> client.migrateServicePoints(servicePoints))
        .compose(v -> runIfPresent(servicePointsUsers, () -> client.migrateServicePointsUsers(servicePointsUsers)))
        .compose(v -> runIfPresent(institutions, () -> client.migrateInstitutions(institutions)))
        .compose(v -> runIfPresent(campuses, () -> client.migrateCampuses(campuses)))
        .compose(v -> runIfPresent(libraries, () -> client.migrateLibraries(libraries)))
        .compose(v -> runIfPresent(locations, () -> client.migrateLocations(locations)))
        .mapEmpty();
    }

    private Future<Void> runIfPresent(Object collection, Supplier<Future<Void>> migration) {
      return collection != null ? migration.get() : Future.succeededFuture();
    }
  }
}
