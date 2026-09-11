package org.folio.support;

import org.folio.it.BaseIntegrationTest;

/**
 * Request paths for {@code *IT} tests, relative to the shared verticle's own base URL (see
 * {@link BaseIntegrationTest}). Not to be confused with {@code org.folio.rest.support.http.InterfaceUrls},
 * which builds absolute URLs against the legacy {@code rest.api} stack's own module instance.
 */
public final class ResourcePaths {

  public static final String INSTANCE_TYPES = "/instance-types";
  public static final String INSTANCE_RELATIONSHIP_TYPES = "/instance-relationship-types";
  public static final String INSTANCES = "/instance-storage/instances";
  public static final String INSTANCE_RELATIONSHIPS = "/instance-storage/instance-relationships";
  public static final String PRECEDING_SUCCEEDING_TITLES = "/preceding-succeeding-titles";
  public static final String LOCATION_UNITS_INSTITUTIONS = "/location-units/institutions";
  public static final String LOCATION_UNITS_CAMPUSES = "/location-units/campuses";
  public static final String LOCATION_UNITS_LIBRARIES = "/location-units/libraries";
  public static final String LOCATIONS = "/locations";
  public static final String SERVICE_POINTS = "/service-points";
  public static final String MATERIAL_TYPES = "/material-types";
  public static final String LOAN_TYPES = "/loan-types";
  public static final String HOLDINGS_SOURCES = "/holdings-sources";
  public static final String HOLDINGS = "/holdings-storage/holdings";
  public static final String HOLDINGS_SYNC = "/holdings-storage/batch/synchronous";
  public static final String HOLDINGS_SYNC_UNSAFE = "/holdings-storage/batch/synchronous-unsafe";
  public static final String HOLDINGS_RETRIEVE = "/holdings-storage/holdings/retrieve";
  public static final String CALL_NUMBER_TYPES = "/call-number-types";
  public static final String ITEMS = "/item-storage/items";
  public static final String ITEMS_RETRIEVE = "/item-storage/items/retrieve";
  public static final String ITEMS_SYNC = "/item-storage/batch/synchronous";
  public static final String ITEMS_SYNC_UNSAFE = "/item-storage/batch/synchronous-unsafe";
  public static final String STATISTICAL_CODE_TYPES = "/statistical-code-types";
  public static final String STATISTICAL_CODES = "/statistical-codes";
  public static final String SUBJECT_TYPES = "/subject-types";
  public static final String SUBJECT_SOURCES = "/subject-sources";
  public static final String INSTANCE_ITERATION = "/instance-storage/instances/iteration";
  public static final String INSTANCE_REINDEX = "/instance-storage/reindex";
  public static final String HRID_SETTINGS = "/hrid-settings-storage/hrid-settings";
  public static final String MIGRATIONS = "/inventory-storage/migrations";
  public static final String MIGRATION_JOBS = "/inventory-storage/migrations/jobs";
  public static final String IDENTIFIER_TYPES = "/identifier-types";
  public static final String CONTRIBUTOR_NAME_TYPES = "/contributor-name-types";
  public static final String INSTANCE_STATUSES = "/instance-statuses";
  public static final String INSTANCES_SYNC = "/instance-storage/batch/synchronous";
  public static final String INSTANCES_SYNC_UNSAFE = "/instance-storage/batch/synchronous-unsafe";
  public static final String INSTANCES_RETRIEVE = "/instance-storage/instances/retrieve";
  public static final String BOUND_WITH_PARTS = "/inventory-storage/bound-with-parts";
  public static final String BOUND_WITHS = "/inventory-storage/bound-withs";
  public static final String INVENTORY_HIERARCHY_UPDATED_INSTANCE_IDS = "/inventory-hierarchy/updated-instance-ids";
  public static final String INVENTORY_HIERARCHY_ITEMS_AND_HOLDINGS = "/inventory-hierarchy/items-and-holdings";
  public static final String OAI_PMH_VIEW_INSTANCES = "/oai-pmh-view/instances";
  public static final String OAI_PMH_VIEW_UPDATED_INSTANCE_IDS = "/oai-pmh-view/updatedInstanceIds";
  public static final String OAI_PMH_VIEW_ENRICHED_INSTANCES = "/oai-pmh-view/enrichedInstances";
  public static final String INVENTORY_SETTINGS = "/inventory-settings";
  public static final String INSTANCE_FORMATS = "/instance-formats";
  public static final String NATURE_OF_CONTENT_TERMS = "/nature-of-content-terms";
  public static final String MODES_OF_ISSUANCE = "/modes-of-issuance";
  public static final String RECORD_BULK_IDS = "/record-bulk/ids";
  public static final String INVENTORY_VIEW_INSTANCES = "/inventory-view/instances";
  public static final String INVENTORY_VIEW_INSTANCE_SET = "/inventory-view/instance-set";
  public static final String INSTANCES_BULK = "/instance-storage/instances/bulk";
  public static final String DEREFERENCED_ITEMS = "/item-storage-dereferenced/items";
  public static final String ALTERNATIVE_TITLE_TYPES = "/alternative-title-types";
  public static final String CLASSIFICATION_TYPES = "/classification-types";
  public static final String CONTRIBUTOR_TYPES = "/contributor-types";
  public static final String ELECTRONIC_ACCESS_RELATIONSHIPS = "/electronic-access-relationships";
  public static final String HOLDINGS_NOTE_TYPES = "/holdings-note-types";
  public static final String HOLDINGS_TYPES = "/holdings-types";
  public static final String ILL_POLICIES = "/ill-policies";
  public static final String ITEM_DAMAGED_STATUSES = "/item-damaged-statuses";
  public static final String INSTANCE_NOTE_TYPES = "/instance-note-types";
  public static final String ITEM_NOTE_TYPES = "/item-note-types";
  public static final String INSTANCE_DATE_TYPES = "/instance-date-types";

  private ResourcePaths() {
  }
}
