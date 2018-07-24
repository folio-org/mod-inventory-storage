package org.folio.rest.support.http;

import org.folio.rest.api.StorageTestSuite;

import java.net.MalformedURLException;
import java.net.URL;

public class InterfaceUrls {
  public static URL materialTypesStorageUrl(String subPath)
    throws MalformedURLException {

    return StorageTestSuite.storageUrl("/material-types" + subPath);
  }

  public static URL loanTypesStorageUrl(String subPath)
    throws MalformedURLException {

    return StorageTestSuite.storageUrl("/loan-types" + subPath);
  }

  public static URL ShelfLocationsStorageUrl(String subPath)
    throws MalformedURLException {

    return StorageTestSuite.storageUrl("/shelf-locations" + subPath);
  }

  public static URL instanceTypesStorageUrl(String subPath)
    throws MalformedURLException {

    return StorageTestSuite.storageUrl("/instance-types" + subPath);
  }

  public static URL itemsStorageUrl(String subPath)
    throws MalformedURLException {

    return StorageTestSuite.storageUrl("/item-storage/items" + subPath);
  }

  public static URL holdingsStorageUrl(String subPath)
    throws MalformedURLException {

    return StorageTestSuite.storageUrl("/holdings-storage/holdings" + subPath);
  }

  public static URL instancesStorageUrl(String subPath)
    throws MalformedURLException {

    return StorageTestSuite.storageUrl("/instance-storage/instances" + subPath);
  }

  public static URL locationsStorageUrl(String subPath)
    throws MalformedURLException {

    return StorageTestSuite.storageUrl("/locations" + subPath);
  }

  public static URL servicePointsUrl(String subPath)
    throws MalformedURLException {
    return StorageTestSuite.storageUrl("/service-points" + subPath);
  }
  
  public static URL servicePointsUsersUrl(String subPath)
      throws MalformedURLException {
    return StorageTestSuite.storageUrl("/service-points-users" + subPath);
  }

  public static URL locInstitutionStorageUrl(String subPath)
    throws MalformedURLException {
    return StorageTestSuite.storageUrl("/location-units/institutions" + subPath);
  }

  public static URL locCampusStorageUrl(String subPath)
    throws MalformedURLException {
    return StorageTestSuite.storageUrl("/location-units/campuses" + subPath);
  }

  public static URL locLibraryStorageUrl(String subPath)
    throws MalformedURLException {
    return StorageTestSuite.storageUrl("/location-units/libraries" + subPath);
  }
}
