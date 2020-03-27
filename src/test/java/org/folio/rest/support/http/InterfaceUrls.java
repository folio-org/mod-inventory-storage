package org.folio.rest.support.http;

import java.net.URL;

import org.folio.rest.api.StorageTestSuite;

public class InterfaceUrls {

  public static URL materialTypesStorageUrl(String subPath) {
    return StorageTestSuite.storageUrl("/material-types" + subPath);
  }

  public static URL loanTypesStorageUrl(String subPath) {
    return StorageTestSuite.storageUrl("/loan-types" + subPath);
  }

  public static URL ShelfLocationsStorageUrl(String subPath) {
    return StorageTestSuite.storageUrl("/shelf-locations" + subPath);
  }

  public static URL instanceTypesStorageUrl(String subPath) {
    return StorageTestSuite.storageUrl("/instance-types" + subPath);
  }

  public static URL itemsStorageUrl(String subPath) {
    return StorageTestSuite.storageUrl("/item-storage/items" + subPath);
  }

  public static URL itemsStorageSyncUrl(String subPath) {
    return StorageTestSuite.storageUrl("/item-storage/batch/synchronous" + subPath);
  }

  public static URL holdingsStorageUrl(String subPath) {
    return StorageTestSuite.storageUrl("/holdings-storage/holdings" + subPath);
  }

  public static URL holdingsStorageSyncUrl(String subPath) {
    return StorageTestSuite.storageUrl("/holdings-storage/batch/synchronous" + subPath);
  }

  public static URL instancesStorageUrl(String subPath) {
    return StorageTestSuite.storageUrl("/instance-storage/instances" + subPath);
  }

  public static URL instancesStorageBatchInstancesUrl(String subPath) {
    return StorageTestSuite.storageUrl("/instance-storage/batch/instances" + subPath);
  }

  public static URL instancesStorageSyncUrl(String subPath) {
    return StorageTestSuite.storageUrl("/instance-storage/batch/synchronous" + subPath);
  }

  public static URL instanceRelationshipsUrl(String subPath) {
    return StorageTestSuite.storageUrl("/instance-storage/instance-relationships" + subPath);
  }

  public static URL precedingSucceedingTitleUrl(String subPath) {
    return StorageTestSuite.storageUrl("/preceding-succeeding-titles" + subPath);
  }

  public static URL instanceRelationshipTypesUrl(String subPath) {
    return StorageTestSuite.storageUrl("/instance-relationship-types" + subPath);
  }

  public static URL contributorTypesUrl(String subPath) {
    return StorageTestSuite.storageUrl("/contributor-types" + subPath);
  }

  public static URL alternativeTitleTypesUrl(String subPath) {
    return StorageTestSuite.storageUrl("/alternative-title-types" + subPath);

  }

  public static URL callNumberTypesUrl(String subPath) {
    return StorageTestSuite.storageUrl("/call-number-types" + subPath);
  }

  public static URL classificationTypesUrl(String subPath) {
    return StorageTestSuite.storageUrl("/classification-types" + subPath);
  }

  public static URL contributorNameTypesUrl(String subPath) {
    return StorageTestSuite.storageUrl("/contributor-name-types" + subPath);
  }

  public static URL electronicAccessRelationshipsUrl(String subPath) {
    return StorageTestSuite.storageUrl("/electronic-access-relationships" + subPath);
  }

  public static URL holdingsNoteTypesUrl(String subPath) {
    return StorageTestSuite.storageUrl("/holdings-note-types" + subPath);
  }

  public static URL holdingsTypesUrl(String subPath) {
    return StorageTestSuite.storageUrl("/holdings-types" + subPath);
  }

  public static URL identifierTypesUrl(String subPath) {
    return StorageTestSuite.storageUrl("/identifier-types" + subPath);
  }

  public static URL illPoliciesUrl(String subPath) {
    return StorageTestSuite.storageUrl("/ill-policies" + subPath);
  }

  public static URL instanceBulkUrl(String subPath) {
    return StorageTestSuite.storageUrl("/instance-bulk" + subPath);
  }

  public static URL instanceFormatsUrl(String subPath) {
    return StorageTestSuite.storageUrl("/instance-formats" + subPath);
  }

  public static URL natureOfContentTermsUrl(String subPath) {
    return StorageTestSuite.storageUrl("/nature-of-content-terms" + subPath);
  }

  public static URL instanceNoteTypesUrl(String subPath) {
    return StorageTestSuite.storageUrl("/instance-note-types" + subPath);
  }

  public static URL instanceStatusesUrl(String subPath) {
    return StorageTestSuite.storageUrl("/instance-statuses" + subPath);
  }

  public static URL instanceTypesUrl(String subPath) {
    return StorageTestSuite.storageUrl("/instance-types" + subPath);
  }

  public static URL itemNoteTypesUrl(String subPath) {
    return StorageTestSuite.storageUrl("/item-note-types" + subPath);
  }

  public static URL itemDamagedStatusesUrl(String subPath) {
    return StorageTestSuite.storageUrl("/item-damaged-statuses" + subPath);
  }

  public static URL modesOfIssuanceUrl(String subPath) {
    return StorageTestSuite.storageUrl("/modes-of-issuance" + subPath);
  }

  public static URL statisticalCodesUrl(String subPath) {
    return StorageTestSuite.storageUrl("/statistical-codes" + subPath);
  }

  public static URL statisticalCodeTypesUrl(String subPath) {
    return StorageTestSuite.storageUrl("/statistical-code-types" + subPath);
  }

  public static URL locationsStorageUrl(String subPath) {
    return StorageTestSuite.storageUrl("/locations" + subPath);
  }

  public static URL servicePointsUrl(String subPath) {
    return StorageTestSuite.storageUrl("/service-points" + subPath);
  }

  public static URL servicePointsUsersUrl(String subPath) {
    return StorageTestSuite.storageUrl("/service-points-users" + subPath);
  }

  public static URL locInstitutionStorageUrl(String subPath) {
    return StorageTestSuite.storageUrl("/location-units/institutions" + subPath);
  }

  public static URL locCampusStorageUrl(String subPath) {
    return StorageTestSuite.storageUrl("/location-units/campuses" + subPath);
  }

  public static URL locLibraryStorageUrl(String subPath) {
    return StorageTestSuite.storageUrl("/location-units/libraries" + subPath);
  }

  public static URL hridSettingsStorageUrl(String subPath) {
    return StorageTestSuite.storageUrl("/hrid-settings-storage/hrid-settings" + subPath);
  }
}
