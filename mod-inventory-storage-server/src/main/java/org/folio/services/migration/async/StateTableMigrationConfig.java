package org.folio.services.migration.async;

import static org.folio.services.migration.MigrationName.INSTANCE_STATISTICAL_CODES;

import org.folio.rest.jaxrs.model.AffectedEntity;

/**
 * Everything table-specific for a state-table background migration that populates a join table
 * from a jsonb array of reference ids.
 *
 * @param migrationName name of the migration, key in the state table
 * @param affectedEntity entity reported in async_migration_job
 * @param sourceTable table with the jsonb array
 * @param jsonbField field of the jsonb with the array of reference ids
 * @param referenceTable table the ids refer to
 * @param joinTable table to populate
 * @param joinSourceColumn join table column with the source record id
 * @param joinReferenceColumn join table column with the reference id
 */
public record StateTableMigrationConfig(String migrationName, AffectedEntity affectedEntity,
                                        String sourceTable, String jsonbField,
                                        String referenceTable, String joinTable,
                                        String joinSourceColumn, String joinReferenceColumn) {

  public static final StateTableMigrationConfig INSTANCE_STATISTICAL_CODES_CONFIG =
    new StateTableMigrationConfig(INSTANCE_STATISTICAL_CODES.getValue(), AffectedEntity.INSTANCE,
      "instance", "statisticalCodeIds", "statistical_code", "instance_statistical_code",
      "instance_id", "statistical_code_id");
}
