package org.folio.rest.api;

import static org.folio.rest.api.TestBaseWithInventoryUtil.ANNEX_LIBRARY_LOCATION;
import static org.folio.rest.api.TestBaseWithInventoryUtil.FOURTH_FLOOR_LOCATION;
import static org.folio.rest.api.TestBaseWithInventoryUtil.MAIN_LIBRARY_LOCATION;
import static org.folio.rest.api.TestBaseWithInventoryUtil.ONLINE_LOCATION;
import static org.folio.rest.api.TestBaseWithInventoryUtil.SECOND_FLOOR_LOCATION;
import static org.folio.rest.api.TestBaseWithInventoryUtil.THIRD_FLOOR_LOCATION;
import static org.folio.rest.api.TestBaseWithInventoryUtil.annexLibraryLocationId;
import static org.folio.rest.api.TestBaseWithInventoryUtil.fourthFloorLocationId;
import static org.folio.rest.api.TestBaseWithInventoryUtil.mainLibraryLocationId;
import static org.folio.rest.api.TestBaseWithInventoryUtil.onlineLocationId;
import static org.folio.rest.api.TestBaseWithInventoryUtil.secondFloorLocationId;
import static org.folio.rest.api.TestBaseWithInventoryUtil.thirdFloorLocationId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Parameter provider for ItemEffectiveLocationTest.class.
 */
public class ItemEffectiveLocationTestDataProvider {
  private static Map<UUID, String> locationIdToNameMap = buildLocationIdToNameMap();

  @SuppressWarnings("unused")
  public static List<PermTemp[]> canCalculateEffectiveLocationOnHoldingUpdateParams() {
    List<PermTemp[]> params = new ArrayList<>();

    PermTemp[] itemLocationsList = {
      new PermTemp(null, null),
      new PermTemp(null, mainLibraryLocationId),
      new PermTemp(mainLibraryLocationId, null),
      new PermTemp(mainLibraryLocationId, annexLibraryLocationId),
    };

    PermTemp[] holdingsStartLocationsList = {
      new PermTemp(onlineLocationId, null),
      new PermTemp(onlineLocationId, secondFloorLocationId),
    };
    PermTemp[] holdingsEndLocationsList = {
      new PermTemp(onlineLocationId, null),
      new PermTemp(onlineLocationId, secondFloorLocationId),
      new PermTemp(thirdFloorLocationId, null),
      new PermTemp(thirdFloorLocationId, fourthFloorLocationId),
    };

    for (PermTemp itemLocations : itemLocationsList) {
      for (PermTemp holdingStartLocations : holdingsStartLocationsList) {
        for (PermTemp holdingEndLocations : holdingsEndLocationsList) {
          params.add(new PermTemp[]{itemLocations, holdingStartLocations, holdingEndLocations});
        }
      }
    }
    return params;
  }

  @SuppressWarnings("unused")
  public static List<PermTemp[]> canCalculateEffectiveLocationOnItemUpdateParams() {
    List<PermTemp[]> parameters = new ArrayList<>();
    PermTemp[] holdingsLocationsList = {
      new PermTemp(mainLibraryLocationId, null),
      new PermTemp(mainLibraryLocationId, annexLibraryLocationId),
    };
    PermTemp[] itemStartLocationsList = {
      new PermTemp(null, null),
      new PermTemp(null, onlineLocationId),
      new PermTemp(onlineLocationId, null),
      new PermTemp(onlineLocationId, secondFloorLocationId),
    };
    PermTemp[] itemEndLocationsList = {
      new PermTemp(null, null),
      new PermTemp(null, onlineLocationId),
      new PermTemp(onlineLocationId, null),
      new PermTemp(onlineLocationId, secondFloorLocationId),
      new PermTemp(null, thirdFloorLocationId),
      new PermTemp(thirdFloorLocationId, null),
      new PermTemp(thirdFloorLocationId, fourthFloorLocationId),
    };

    for (PermTemp holdingLocations : holdingsLocationsList) {
      for (PermTemp itemStartLocations : itemStartLocationsList) {
        for (PermTemp itemEndLocations : itemEndLocationsList) {
          parameters.add(new PermTemp[]{holdingLocations, itemStartLocations, itemEndLocations});
        }
      }
    }
    return parameters;
  }

  private static Map<UUID, String> buildLocationIdToNameMap() {
    HashMap<UUID, String> idToNameMap = new HashMap<>();

    idToNameMap.put(mainLibraryLocationId, MAIN_LIBRARY_LOCATION);
    idToNameMap.put(annexLibraryLocationId, ANNEX_LIBRARY_LOCATION);
    idToNameMap.put(onlineLocationId, ONLINE_LOCATION);
    idToNameMap.put(secondFloorLocationId, SECOND_FLOOR_LOCATION);
    idToNameMap.put(thirdFloorLocationId, THIRD_FLOOR_LOCATION);
    idToNameMap.put(fourthFloorLocationId, FOURTH_FLOOR_LOCATION);

    return idToNameMap;
  }

  /**
   * Store a permanent location UUID and a temporary location UUID.
   */
  public static class PermTemp {
    /**
     * permanent location UUID
     */
    UUID perm;
    /**
     * temporary location UUID
     */
    UUID temp;

    /**
     * @param perm permanent location UUID
     * @param temp temporary location UUID
     */
    PermTemp(UUID perm, UUID temp) {
      this.perm = perm;
      this.temp = temp;
    }

    @Override
    public String toString() {
      return locationIdToNameMap.get(perm) + ", " + locationIdToNameMap.get(temp);
    }
  }

}
