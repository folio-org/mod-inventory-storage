package org.folio.services.migration;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.Map;
import org.folio.client.LocationMigrationClient;
import org.folio.persist.CampusRepository;
import org.folio.persist.InstitutionRepository;
import org.folio.persist.LibraryRepository;
import org.folio.persist.LocationRepository;
import org.folio.persist.ServicePointRepository;
import org.folio.persist.ServicePointsUserRepository;
import org.folio.rest.jaxrs.model.LocationCampuses;
import org.folio.rest.jaxrs.model.LocationInstitutions;
import org.folio.rest.jaxrs.model.LocationLibraries;
import org.folio.rest.jaxrs.model.Locations;
import org.folio.rest.jaxrs.model.ServicePoints;
import org.folio.rest.jaxrs.model.ServicePointsUsers;
import org.folio.rest.persist.Criteria.Criterion;

public class LocationMigrationService {

  private final Context vertxContext;
  private final Map<String, String> okapiHeaders;

  public LocationMigrationService(Context vertxContext, Map<String, String> okapiHeaders) {
    this.vertxContext = vertxContext;
    this.okapiHeaders = okapiHeaders;
  }

  public Future<Void> migrate() {
    var institutions = new InstitutionRepository(vertxContext, okapiHeaders);
    var campuses = new CampusRepository(vertxContext, okapiHeaders);
    var libraries = new LibraryRepository(vertxContext, okapiHeaders);
    var locations = new LocationRepository(vertxContext, okapiHeaders);
    var servicePoints = new ServicePointRepository(vertxContext, okapiHeaders);
    var servicePointsUsers = new ServicePointsUserRepository(vertxContext, okapiHeaders);
    var criterion = new Criterion();

    return Future.all(
        institutions.get(criterion),
        campuses.get(criterion),
        libraries.get(criterion),
        locations.get(criterion),
        servicePoints.get(criterion),
        servicePointsUsers.get(criterion)
      )
      .compose(results -> LocationMigrationClient.builder(vertxContext.owner(), okapiHeaders)
        .institutions(new LocationInstitutions().withLocinsts(results.resultAt(0)))
        .campuses(new LocationCampuses().withLoccamps(results.resultAt(1)))
        .libraries(new LocationLibraries().withLoclibs(results.resultAt(2)))
        .locations(new Locations().withLocations(results.resultAt(3)))
        .servicePoints(new ServicePoints().withServicepoints(results.resultAt(4)))
        .servicePointsUsers(new ServicePointsUsers().withServicePointsUsers(results.resultAt(5)))
        .migrate());
  }
}
