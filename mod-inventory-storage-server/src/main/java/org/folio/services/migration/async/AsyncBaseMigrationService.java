package org.folio.services.migration.async;

import io.vertx.core.Future;
import java.util.Map;
import java.util.Set;

/**
 * Processes the records of one asynchronous migration. Implementations are stateless beans: the tenant comes from
 * the {@code headers} of each call.
 */
public interface AsyncBaseMigrationService {

  String getMigrationName();

  Future<Void> runMigrationForIds(Set<String> ids, Map<String, String> headers);
}
