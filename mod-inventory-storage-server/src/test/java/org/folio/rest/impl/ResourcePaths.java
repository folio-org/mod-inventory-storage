package org.folio.rest.impl;

/**
 * Request paths for {@code *IT} tests, relative to the shared verticle's own base URL (see
 * {@link BaseIntegrationTest}). Not to be confused with {@code org.folio.rest.support.http.InterfaceUrls},
 * which builds absolute URLs against the legacy {@code rest.api} stack's own module instance.
 */
final class ResourcePaths {

  static final String INSTANCE_TYPES = "/instance-types";
  static final String INSTANCE_RELATIONSHIP_TYPES = "/instance-relationship-types";
  static final String INSTANCES = "/instance-storage/instances";
  static final String INSTANCE_RELATIONSHIPS = "/instance-storage/instance-relationships";
  static final String PRECEDING_SUCCEEDING_TITLES = "/preceding-succeeding-titles";
  static final String LOCATION_UNITS_INSTITUTIONS = "/location-units/institutions";
  static final String LOCATION_UNITS_CAMPUSES = "/location-units/campuses";
  static final String LOCATION_UNITS_LIBRARIES = "/location-units/libraries";
  static final String LOCATIONS = "/locations";
  static final String SERVICE_POINTS = "/service-points";
  static final String MATERIAL_TYPES = "/material-types";
  static final String LOAN_TYPES = "/loan-types";
  static final String HOLDINGS_SOURCES = "/holdings-sources";
  static final String HOLDINGS = "/holdings-storage/holdings";
  static final String HOLDINGS_SYNC = "/holdings-storage/batch/synchronous";
  static final String HOLDINGS_SYNC_UNSAFE = "/holdings-storage/batch/synchronous-unsafe";
  static final String HOLDINGS_RETRIEVE = "/holdings-storage/holdings/retrieve";
  static final String CALL_NUMBER_TYPES = "/call-number-types";
  static final String ITEMS = "/item-storage/items";
  static final String SUBJECT_TYPES = "/subject-types";
  static final String SUBJECT_SOURCES = "/subject-sources";
  static final String INSTANCE_ITERATION = "/instance-storage/instances/iteration";
  static final String INSTANCE_REINDEX = "/instance-storage/reindex";
  static final String HRID_SETTINGS = "/hrid-settings-storage/hrid-settings";
  static final String MIGRATIONS = "/inventory-storage/migrations";
  static final String MIGRATION_JOBS = "/inventory-storage/migrations/jobs";
  static final String IDENTIFIER_TYPES = "/identifier-types";
  static final String CONTRIBUTOR_NAME_TYPES = "/contributor-name-types";
  static final String INSTANCE_STATUSES = "/instance-statuses";
  static final String INSTANCES_SYNC = "/instance-storage/batch/synchronous";
  static final String INSTANCES_SYNC_UNSAFE = "/instance-storage/batch/synchronous-unsafe";
  static final String INSTANCES_RETRIEVE = "/instance-storage/instances/retrieve";

  private ResourcePaths() {
  }
}
