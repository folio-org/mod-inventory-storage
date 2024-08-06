package org.folio.rest.support.http;

import static org.folio.utility.ModuleUtility.vertxUrl;

import java.net.URL;

public class InterfaceUrls {

  public static URL materialTypesStorageUrl(String subPath) {
    return vertxUrl("/material-types" + subPath);
  }

  public static URL loanTypesStorageUrl(String subPath) {
    return vertxUrl("/loan-types" + subPath);
  }

  public static URL shelfLocationsStorageUrl(String subPath) {
    return vertxUrl("/shelf-locations" + subPath);
  }

  public static URL instanceTypesStorageUrl(String subPath) {
    return vertxUrl("/instance-types" + subPath);
  }

  public static URL subjectTypesUrl(String subPath) {
    return vertxUrl("/subject-types" + subPath);
  }

  public static URL subjectSourcesUrl(String subPath) {
    return vertxUrl("/subject-sources" + subPath);
  }

  public static URL itemsStorageUrl(String subPath) {
    return vertxUrl("/item-storage/items" + subPath);
  }

  public static URL itemsStorageSyncUrl(String subPath) {
    return vertxUrl("/item-storage/batch/synchronous" + subPath);
  }

  public static URL itemsStorageSyncUnsafeUrl(String subPath) {
    return vertxUrl("/item-storage/batch/synchronous-unsafe" + subPath);
  }

  public static URL holdingsStorageUrl(String subPath) {
    return vertxUrl("/holdings-storage/holdings" + subPath);
  }

  public static URL holdingsStorageSyncUrl(String subPath) {
    return vertxUrl("/holdings-storage/batch/synchronous" + subPath);
  }

  public static URL holdingsStorageSyncUnsafeUrl(String subPath) {
    return vertxUrl("/holdings-storage/batch/synchronous-unsafe" + subPath);
  }

  public static URL holdingsSourceUrl(String subPath) {
    return vertxUrl("/holdings-sources" + subPath);
  }

  public static URL instancesStorageUrl(String subPath) {
    return vertxUrl("/instance-storage/instances" + subPath);
  }

  public static URL instancesStorageBatchInstancesUrl(String subPath) {
    return vertxUrl("/instance-storage/batch/instances" + subPath);
  }

  public static URL instancesStorageSyncUrl(String subPath) {
    return vertxUrl("/instance-storage/batch/synchronous" + subPath);
  }

  public static URL instancesStorageSyncUnsafeUrl(String subPath) {
    return vertxUrl("/instance-storage/batch/synchronous-unsafe" + subPath);
  }

  public static URL instanceSetUrl(String subPath) {
    return vertxUrl("/inventory-view/instance-set" + subPath);
  }

  public static URL instanceRelationshipsUrl(String subPath) {
    return vertxUrl("/instance-storage/instance-relationships" + subPath);
  }

  public static URL precedingSucceedingTitleUrl(String subPath) {
    return vertxUrl("/preceding-succeeding-titles" + subPath);
  }

  public static URL instanceRelationshipTypesUrl(String subPath) {
    return vertxUrl("/instance-relationship-types" + subPath);
  }

  public static URL boundWithPartsUrl(String subPath) {
    return vertxUrl("/inventory-storage/bound-with-parts" + subPath);
  }

  public static URL boundWithsUrl() {
    return vertxUrl("/inventory-storage/bound-withs");
  }

  public static URL contributorTypesUrl(String subPath) {
    return vertxUrl("/contributor-types" + subPath);
  }

  public static URL alternativeTitleTypesUrl(String subPath) {
    return vertxUrl("/alternative-title-types" + subPath);
  }

  public static URL callNumberTypesUrl(String subPath) {
    return vertxUrl("/call-number-types" + subPath);
  }

  public static URL classificationTypesUrl(String subPath) {
    return vertxUrl("/classification-types" + subPath);
  }

  public static URL contributorNameTypesUrl(String subPath) {
    return vertxUrl("/contributor-name-types" + subPath);
  }

  public static URL electronicAccessRelationshipsUrl(String subPath) {
    return vertxUrl("/electronic-access-relationships" + subPath);
  }

  public static URL holdingsNoteTypesUrl(String subPath) {
    return vertxUrl("/holdings-note-types" + subPath);
  }

  public static URL holdingsTypesUrl(String subPath) {
    return vertxUrl("/holdings-types" + subPath);
  }

  public static URL identifierTypesUrl(String subPath) {
    return vertxUrl("/identifier-types" + subPath);
  }

  public static URL illPoliciesUrl(String subPath) {
    return vertxUrl("/ill-policies" + subPath);
  }

  public static URL recordBulkUrl(String subPath) {
    return vertxUrl("/record-bulk" + subPath);
  }

  public static URL instanceFormatsUrl(String subPath) {
    return vertxUrl("/instance-formats" + subPath);
  }

  public static URL natureOfContentTermsUrl(String subPath) {
    return vertxUrl("/nature-of-content-terms" + subPath);
  }

  public static URL instanceNoteTypesUrl(String subPath) {
    return vertxUrl("/instance-note-types" + subPath);
  }

  public static URL instanceStatusesUrl(String subPath) {
    return vertxUrl("/instance-statuses" + subPath);
  }

  public static URL instanceTypesUrl(String subPath) {
    return vertxUrl("/instance-types" + subPath);
  }

  public static URL itemNoteTypesUrl(String subPath) {
    return vertxUrl("/item-note-types" + subPath);
  }

  public static URL itemDamagedStatusesUrl(String subPath) {
    return vertxUrl("/item-damaged-statuses" + subPath);
  }

  public static URL modesOfIssuanceUrl(String subPath) {
    return vertxUrl("/modes-of-issuance" + subPath);
  }

  public static URL statisticalCodesUrl(String subPath) {
    return vertxUrl("/statistical-codes" + subPath);
  }

  public static URL statisticalCodeTypesUrl(String subPath) {
    return vertxUrl("/statistical-code-types" + subPath);
  }

  public static URL locationsStorageUrl(String subPath) {
    return vertxUrl("/locations" + subPath);
  }

  public static URL servicePointsUrl(String subPath) {
    return vertxUrl("/service-points" + subPath);
  }

  public static URL servicePointsUsersUrl(String subPath) {
    return vertxUrl("/service-points-users" + subPath);
  }

  public static URL locInstitutionStorageUrl(String subPath) {
    return vertxUrl("/location-units/institutions" + subPath);
  }

  public static URL locCampusStorageUrl(String subPath) {
    return vertxUrl("/location-units/campuses" + subPath);
  }

  public static URL locLibraryStorageUrl(String subPath) {
    return vertxUrl("/location-units/libraries" + subPath);
  }

  public static URL hridSettingsStorageUrl(String subPath) {
    return vertxUrl("/hrid-settings-storage/hrid-settings" + subPath);
  }

  // TODO: These entries will be removed soon
  public static URL oaiPmhView(String subPath) {
    return vertxUrl("/oai-pmh-view/instances" + subPath);
  }

  public static URL oaiPmhViewUpdatedInstanceIds(String subPath) {
    return vertxUrl("/oai-pmh-view/updatedInstanceIds" + subPath);
  }

  public static URL oaiPmhViewEnrichedInstances() {
    return vertxUrl("/oai-pmh-view/enrichedInstances");
  }

  /**
   * EoF - these entries will be removed soon.
   */
  public static URL inventoryHierarchyUpdatedInstanceIds(String subPath) {
    return vertxUrl("/inventory-hierarchy/updated-instance-ids" + subPath);
  }

  public static URL inventoryHierarchyItemsAndHoldings() {
    return vertxUrl("/inventory-hierarchy/items-and-holdings");
  }

  public static URL inventoryViewInstances(String path) {
    return vertxUrl("/inventory-view/instances" + path);
  }

  public static URL instanceReindex(String path) {
    return vertxUrl("/instance-storage/reindex" + path);
  }

  public static URL dereferencedItemStorage(String path) {
    return vertxUrl("/item-storage-dereferenced/items" + path);
  }

  public static URL instanceIteration(String path) {
    return vertxUrl("/instance-storage/instances/iteration" + path);
  }

  public static URL migrationsUrl(String subPath) {
    return vertxUrl("/inventory-storage/migrations" + subPath);
  }

  public static URL migrationJobsUrl(String subPath) {
    return vertxUrl("/inventory-storage/migrations/jobs" + subPath);
  }

}
