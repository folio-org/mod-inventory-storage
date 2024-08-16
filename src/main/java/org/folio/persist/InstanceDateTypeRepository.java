package org.folio.persist;

import org.folio.rest.jaxrs.model.InstanceDateType;
import org.folio.rest.persist.PostgresClient;

public class InstanceDateTypeRepository extends AbstractRepository<InstanceDateType> {

  public InstanceDateTypeRepository(PostgresClient postgresClient, String tableName,
                                    Class<InstanceDateType> recordType) {
    super(postgresClient, tableName, recordType);
  }
}
