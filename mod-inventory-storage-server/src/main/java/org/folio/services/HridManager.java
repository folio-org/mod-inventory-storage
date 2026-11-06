package org.folio.services;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.persist.HridSettingsRepository.HRID_HOLDINGS_SEQUENCE_NAME;
import static org.folio.persist.HridSettingsRepository.HRID_INSTANCES_SEQUENCE_NAME;
import static org.folio.persist.HridSettingsRepository.HRID_ITEMS_SEQUENCE_NAME;

import io.vertx.core.Future;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.persist.HridSettingsRepository;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.HridSettings;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.Item;
import org.springframework.stereotype.Component;

@Component
public class HridManager {

  private static final Logger log = LogManager.getLogger();

  private final HridSettingsRepository repository;

  public HridManager(HridSettingsRepository repository) {
    this.repository = Objects.requireNonNull(repository, "Repository cannot be null");
  }

  public Future<Item> populateHrid(Item item, String tenantId) {
    return populateHridForItems(List.of(item), tenantId).map(item);
  }

  public Future<Instance> populateHrid(Instance instance, String tenantId) {
    return populateHridForInstances(List.of(instance), tenantId).map(instance);
  }

  public Future<HoldingsRecord> populateHrid(HoldingsRecord hr, String tenantId) {
    return populateHridForHoldings(List.of(hr), tenantId).map(hr);
  }

  public Future<List<Instance>> populateHridForInstances(List<Instance> instances, String tenantId) {
    return populateHrids(InventoryType.INSTANCES, instances, Instance::getHrid, Instance::setHrid, tenantId);
  }

  public Future<List<Item>> populateHridForItems(List<Item> items, String tenantId) {
    return populateHrids(InventoryType.ITEMS, items, Item::getHrid, Item::setHrid, tenantId);
  }

  public Future<List<HoldingsRecord>> populateHridForHoldings(List<HoldingsRecord> hrs, String tenantId) {
    return populateHrids(InventoryType.HOLDINGS, hrs, HoldingsRecord::getHrid, HoldingsRecord::setHrid, tenantId);
  }

  public Future<HridSettings> getHridSettings(String tenantId) {
    return repository.getSettings(tenantId)
      .onFailure(e -> log.error("Failed to retrieve the HRID settings", e));
  }

  public Future<Void> updateHridSettings(HridSettings hridSettings, String tenantId) {
    hridSettings.getInstances().setCurrentNumber(null);
    hridSettings.getHoldings().setCurrentNumber(null);
    hridSettings.getItems().setCurrentNumber(null);
    return repository.updateSettings(hridSettings, tenantId);
  }

  private <T> Future<List<T>> populateHrids(InventoryType inventoryType, List<T> list,
                                            Function<T, String> getHrid, BiConsumer<T, String> setHrid,
                                            String tenantId) {

    int n = 0;
    for (T t : list) {
      if (isBlank(getHrid.apply(t))) {
        n++;
      }
    }
    return getNextHrids(inventoryType, n, tenantId)
      .map(List::iterator)
      .map(hrids -> {
        for (T t : list) {
          if (isBlank(getHrid.apply(t))) {
            setHrid.accept(t, hrids.next());
          }
        }
        return list;
      });
  }

  /**
   * Return the next n HRIDs for the given type.
   */
  private Future<List<String>> getNextHrids(InventoryType type, int n, String tenantId) {
    if (n == 0) {
      return Future.succeededFuture(Collections.emptyList());
    }
    return repository.getSettingsAndNextValues(type.getSequenceName(), n, tenantId)
      .map(settingsAndValues -> {
        HridSettings hridSettings = settingsAndValues.settings();
        final String hridPrefix = type.getPrefix(hridSettings);
        final String formatter = getHridFormatter(hridSettings);
        List<String> list = new ArrayList<>(n);
        for (int i = 1; i <= n; i++) {
          String hrid = String.format(
            formatter,
            Objects.toString(hridPrefix, ""),
            settingsAndValues.values().get(i - 1));
          list.add(hrid);
        }
        return list;
      });
  }

  private String getHridFormatter(HridSettings hridSettings) {
    return Boolean.TRUE.equals(hridSettings.getCommonRetainLeadingZeroes()) ? "%s%011d" : "%s%d";
  }

  private enum InventoryType implements Inventory {
    HOLDINGS {
      @Override
      public String getPrefix(HridSettings hridSettings) {
        return hridSettings.getHoldings().getPrefix();
      }

      @Override
      public String getSequenceName() {
        return HRID_HOLDINGS_SEQUENCE_NAME;
      }
    },

    INSTANCES {
      @Override
      public String getPrefix(HridSettings hridSettings) {
        return hridSettings.getInstances().getPrefix();
      }

      @Override
      public String getSequenceName() {
        return HRID_INSTANCES_SEQUENCE_NAME;
      }
    },

    ITEMS {
      @Override
      public String getPrefix(HridSettings hridSettings) {
        return hridSettings.getItems().getPrefix();
      }

      @Override
      public String getSequenceName() {
        return HRID_ITEMS_SEQUENCE_NAME;
      }
    }
  }

  private interface Inventory {
    String getPrefix(HridSettings hridSettings);

    String getSequenceName();
  }
}
