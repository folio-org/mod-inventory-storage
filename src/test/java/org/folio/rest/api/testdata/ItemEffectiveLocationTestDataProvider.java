package org.folio.rest.api.testdata;

import static org.folio.rest.api.TestBaseWithInventoryUtil.ANNEX_LIBRARY_LOCATION;
import static org.folio.rest.api.TestBaseWithInventoryUtil.ANNEX_LIBRARY_LOCATION_ID;
import static org.folio.rest.api.TestBaseWithInventoryUtil.FOURTH_FLOOR_LOCATION;
import static org.folio.rest.api.TestBaseWithInventoryUtil.FOURTH_FLOOR_LOCATION_ID;
import static org.folio.rest.api.TestBaseWithInventoryUtil.MAIN_LIBRARY_LOCATION;
import static org.folio.rest.api.TestBaseWithInventoryUtil.MAIN_LIBRARY_LOCATION_ID;
import static org.folio.rest.api.TestBaseWithInventoryUtil.ONLINE_LOCATION;
import static org.folio.rest.api.TestBaseWithInventoryUtil.ONLINE_LOCATION_ID;
import static org.folio.rest.api.TestBaseWithInventoryUtil.SECOND_FLOOR_LOCATION;
import static org.folio.rest.api.TestBaseWithInventoryUtil.SECOND_FLOOR_LOCATION_ID;
import static org.folio.rest.api.TestBaseWithInventoryUtil.THIRD_FLOOR_LOCATION;
import static org.folio.rest.api.TestBaseWithInventoryUtil.THIRD_FLOOR_LOCATION_ID;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Parameter provider for ItemEffectiveLocationTest.class.
 */
public class ItemEffectiveLocationTestDataProvider {
  private static final Map<UUID, String> LOCATION_ID_TO_NAME_MAP = buildLocationIdToNameMap();

  @SuppressWarnings("unused")
  public static List<PermTemp[]> canCalculateEffectiveLocationOnHoldingUpdateParams() {
    PermTemp[] itemLocationsList = createItemLocationsList();
    PermTemp[] holdingsStartLocationsList = createHoldingsStartLocationsList();
    PermTemp[] holdingsEndLocationsList = createHoldingsEndLocationsList();

    return generateHoldingsUpdateParams(itemLocationsList, holdingsStartLocationsList, holdingsEndLocationsList);
  }

  private static PermTemp[] createItemLocationsList() {
    return new PermTemp[] {
      new PermTemp(null, null),
      new PermTemp(null, MAIN_LIBRARY_LOCATION_ID),
      new PermTemp(MAIN_LIBRARY_LOCATION_ID, null),
      new PermTemp(MAIN_LIBRARY_LOCATION_ID, ANNEX_LIBRARY_LOCATION_ID)
    };
  }

  private static PermTemp[] createHoldingsStartLocationsList() {
    return new PermTemp[] {
      new PermTemp(ONLINE_LOCATION_ID, null),
      new PermTemp(ONLINE_LOCATION_ID, SECOND_FLOOR_LOCATION_ID)
    };
  }

  private static PermTemp[] createHoldingsEndLocationsList() {
    return new PermTemp[] {
      new PermTemp(ONLINE_LOCATION_ID, null),
      new PermTemp(ONLINE_LOCATION_ID, SECOND_FLOOR_LOCATION_ID),
      new PermTemp(THIRD_FLOOR_LOCATION_ID, null),
      new PermTemp(THIRD_FLOOR_LOCATION_ID, FOURTH_FLOOR_LOCATION_ID)
    };
  }

  private static List<PermTemp[]> generateHoldingsUpdateParams(PermTemp[] itemLocationsList,
                                                                PermTemp[] holdingsStartLocationsList,
                                                                PermTemp[] holdingsEndLocationsList) {
    List<PermTemp[]> params = new ArrayList<>();
    for (PermTemp itemLocations : itemLocationsList) {
      for (PermTemp holdingStartLocations : holdingsStartLocationsList) {
        for (PermTemp holdingEndLocations : holdingsEndLocationsList) {
          params.add(new PermTemp[] {itemLocations, holdingStartLocations, holdingEndLocations});
        }
      }
    }
    return params;
  }

  @SuppressWarnings("unused")
  public static List<PermTemp[]> canCalculateEffectiveLocationOnItemUpdateParams() {
    PermTemp[] holdingsLocationsList = createHoldingsLocationsList();
    PermTemp[] itemStartLocationsList = createItemStartLocationsList();
    PermTemp[] itemEndLocationsList = createItemEndLocationsList();

    return generateItemUpdateParams(holdingsLocationsList, itemStartLocationsList, itemEndLocationsList);
  }

  private static PermTemp[] createHoldingsLocationsList() {
    return new PermTemp[] {
      new PermTemp(MAIN_LIBRARY_LOCATION_ID, null),
      new PermTemp(MAIN_LIBRARY_LOCATION_ID, ANNEX_LIBRARY_LOCATION_ID)
    };
  }

  private static PermTemp[] createItemStartLocationsList() {
    return new PermTemp[] {
      new PermTemp(null, null),
      new PermTemp(null, ONLINE_LOCATION_ID),
      new PermTemp(ONLINE_LOCATION_ID, null),
      new PermTemp(ONLINE_LOCATION_ID, SECOND_FLOOR_LOCATION_ID)
    };
  }

  private static PermTemp[] createItemEndLocationsList() {
    return new PermTemp[] {
      new PermTemp(null, null),
      new PermTemp(null, ONLINE_LOCATION_ID),
      new PermTemp(ONLINE_LOCATION_ID, null),
      new PermTemp(ONLINE_LOCATION_ID, SECOND_FLOOR_LOCATION_ID),
      new PermTemp(null, THIRD_FLOOR_LOCATION_ID),
      new PermTemp(THIRD_FLOOR_LOCATION_ID, null),
      new PermTemp(THIRD_FLOOR_LOCATION_ID, FOURTH_FLOOR_LOCATION_ID)
    };
  }

  private static List<PermTemp[]> generateItemUpdateParams(PermTemp[] holdingsLocationsList,
                                                            PermTemp[] itemStartLocationsList,
                                                            PermTemp[] itemEndLocationsList) {
    List<PermTemp[]> parameters = new ArrayList<>();
    for (PermTemp holdingLocations : holdingsLocationsList) {
      for (PermTemp itemStartLocations : itemStartLocationsList) {
        for (PermTemp itemEndLocations : itemEndLocationsList) {
          parameters.add(new PermTemp[] {holdingLocations, itemStartLocations, itemEndLocations});
        }
      }
    }
    return parameters;
  }

  private static Map<UUID, String> buildLocationIdToNameMap() {
    HashMap<UUID, String> idToNameMap = new HashMap<>();

    idToNameMap.put(MAIN_LIBRARY_LOCATION_ID, MAIN_LIBRARY_LOCATION);
    idToNameMap.put(ANNEX_LIBRARY_LOCATION_ID, ANNEX_LIBRARY_LOCATION);
    idToNameMap.put(ONLINE_LOCATION_ID, ONLINE_LOCATION);
    idToNameMap.put(SECOND_FLOOR_LOCATION_ID, SECOND_FLOOR_LOCATION);
    idToNameMap.put(THIRD_FLOOR_LOCATION_ID, THIRD_FLOOR_LOCATION);
    idToNameMap.put(FOURTH_FLOOR_LOCATION_ID, FOURTH_FLOOR_LOCATION);

    return idToNameMap;
  }

  /**
   * Store a permanent location UUID and a temporary location UUID.
   */
  public static class PermTemp {
    /**
     * permanent location UUID.
     */
    public UUID perm;
    /**
     * temporary location UUID.
     */
    public UUID temp;

    /**
     * Constructor.
     *
     * @param perm permanent location UUID
     * @param temp temporary location UUID
     */
    PermTemp(UUID perm, UUID temp) {
      this.perm = perm;
      this.temp = temp;
    }

    @Override
    public String toString() {
      return LOCATION_ID_TO_NAME_MAP.get(perm) + ", " + LOCATION_ID_TO_NAME_MAP.get(temp);
    }
  }
}
