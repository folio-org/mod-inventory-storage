package org.folio.persist;

import io.vertx.core.Context;

/**
 * Repository for simple reference data tables that have no dedicated repository. Not a Spring bean on its own,
 * instances are managed by {@link org.folio.services.referencedata.ReferenceDataService}.
 */
public class ReferenceDataRepository<T> extends AbstractRepository<T> {

  public ReferenceDataRepository(Context context, String tableName, Class<T> recordType) {
    super(context, tableName, recordType);
  }
}
