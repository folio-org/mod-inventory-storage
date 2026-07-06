package org.folio.services.setting;

import static org.folio.okapi.common.XOkapiHeaders.TENANT;
import static org.folio.services.consortium.entities.Settings.INVENTORY_OPTIMIZE_UPDATES_ENABLED;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.persist.SettingsRepository;
import org.folio.rest.exceptions.BadRequestException;
import org.folio.rest.exceptions.NotFoundException;
import org.folio.rest.jaxrs.model.Setting;
import org.folio.services.caches.ConsortiumDataCache;
import org.folio.services.caches.SettingCache;
import org.folio.services.consortium.ConsortiumService;
import org.folio.services.consortium.ConsortiumServiceImpl;
import org.folio.services.domainevent.SettingEvent;
import org.folio.services.domainevent.SettingEventPublisher;
import org.folio.validator.SettingsValidator;

public class SettingsService {

  private static final Logger logger = LogManager.getLogger(SettingsService.class);

  private final Context context;
  private final SettingsRepository settingsRepository;
  private final ConsortiumService consortiumService;
  private final SettingCache cache;
  private final SettingsValidator validator;
  private final SettingEventPublisher publisher;

  public SettingsService(Context context, Map<String, String> okapiHeaders) {
    this.context = context;
    this.settingsRepository = new SettingsRepository(context, okapiHeaders);
    this.consortiumService = new ConsortiumServiceImpl(context.owner().createHttpClient(),
      context.get(ConsortiumDataCache.class.getName()));
    this.cache = context.get(SettingCache.class.getName());
    this.validator = new SettingsValidator();
    this.publisher = new SettingEventPublisher(context);
  }

  public Future<Setting> getSettingByKey(String key) {
    return settingsRepository.findByKey(key)
      .compose(entity -> {
        if (entity == null) {
          logger.warn("getSettingByKey:: Setting not found: {}", key);
          return Future.failedFuture(new NotFoundException("Setting not found: " + key));
        }
        return Future.succeededFuture(entity);
      });
  }

  public Future<Void> updateSetting(String key, Object value, Map<String, String> okapiHeaders) {
    return getSettingByKey(key)
      .compose(existingSetting -> {
        if (existingSetting.getCentralManaged() == null || !existingSetting.getCentralManaged().booleanValue()) {
          return updateSettingAndPublishEvent(value, okapiHeaders, existingSetting);
        }
        return consortiumService.getConsortiumData(okapiHeaders)
          .compose(consortiumData -> {
            if (consortiumData.isEmpty()) {
              return updateSettingAndPublishEvent(value, okapiHeaders, existingSetting);
            }
            var consortium = consortiumData.get();
            var tenantId = okapiHeaders.get(TENANT);
            if (!consortium.centralTenantId().equalsIgnoreCase(tenantId)) {
              var message = String.format("Consortium member tenant %s cannot update setting: %s", tenantId, key);
              logger.warn("updateSetting:: {}", message);
              return Future.failedFuture(new BadRequestException(message));
            }
            return updateSettingAcrossConsortium(value, okapiHeaders, existingSetting, consortium.memberTenants());
          });
      })
      .onSuccess(v -> logger.debug("Setting updated with key {} and value {}", key, value))
      .onFailure(t -> logger.error("Error updating setting with key {}", key, t)
      );
  }

  public Future<Boolean> isOptimizeUpdatesEnabled(String tenantId) {
    return getCachedSettingValue(tenantId, INVENTORY_OPTIMIZE_UPDATES_ENABLED.getValue())
      .map(Boolean::parseBoolean);
  }

  private Future<Void> updateSettingAcrossConsortium(Object value, Map<String, String> okapiHeaders,
                                                     Setting existingSetting, List<String> memberTenants) {
    return updateSettingByKey(value, existingSetting, okapiHeaders)
      .compose(updatedSetting -> {
        // publish event for central tenant
        publishSettingEvent(updatedSetting, okapiHeaders);
        var updateFutures = new ArrayList<Future<Void>>();
        buildMemberTenantUpdateFutures(okapiHeaders, updatedSetting, memberTenants, updateFutures);
        return Future.all(updateFutures)
          .onFailure(t -> logger.error("Error updating setting across consortium for key {}",
            updatedSetting.getKey(), t))
          .mapEmpty();
      })
      .onFailure(t -> logger.error("Error updating setting across consortium for key {}",
        existingSetting.getKey(), t)).mapEmpty();
  }

  private void buildMemberTenantUpdateFutures(Map<String, String> okapiHeaders, Setting updatedSetting,
                                              List<String> memberTenants, List<Future<Void>> futures) {
    var value = updatedSetting.getValue();
    var key = updatedSetting.getKey();
    for (String memberTenantId : memberTenants) {
      var headers = new HashMap<>(okapiHeaders);
      headers.put(TENANT, memberTenantId);
      var repository = new SettingsRepository(context, headers);
      futures.add(repository.update(updatedSetting)
        .onSuccess(memberSetting -> {
          logger.debug("Setting {} updated for tenant {} with value {}. Publishing setting event...",
            key, memberTenantId, value);
          // publish event for member tenant
          publishSettingEvent(memberSetting, headers);
        })
        .onFailure(t -> logger.error("Error updating tenant {} setting key {}", memberTenantId, key, t))
        .mapEmpty()
      );
    }
  }

  private Future<Void> updateSettingAndPublishEvent(Object value, Map<String, String> okapiHeaders,
                                                    Setting existingSetting) {
    return updateSettingByKey(value, existingSetting, okapiHeaders)
      .onSuccess(updatedSetting -> publishSettingEvent(updatedSetting, okapiHeaders))
      .mapEmpty();
  }

  private Future<String> getCachedSettingValue(String tenantId, String key) {
    var cacheKey = tenantId + ":" + key;
    return cache.get(cacheKey, (ignored, executor) -> loadSettingValue(key)
      .toCompletionStage()
      .toCompletableFuture());
  }

  private Future<String> loadSettingValue(String key) {
    return settingsRepository.findByKey(key)
      .compose(entity -> {
        if (entity == null) {
          logger.warn("Setting not found: {}", key);
          return Future.failedFuture(new NotFoundException("Setting not found: " + key));
        }
        logger.info("Setting found: {}, value: {}", key, entity.getValue());
        return Future.succeededFuture(entity.getValue());
      });
  }

  private Future<Setting> updateSettingByKey(Object value, Setting existingSetting,
                                             Map<String, String> okapiHeaders) {
    var key = existingSetting.getKey();
    logger.debug("Updating setting: {}", key);
    validator.validate(value, existingSetting);
    existingSetting.setValue(value.toString());
    existingSetting.setUpdatedDate(new Date());
    existingSetting.setUpdatedByUserId(getUserId(okapiHeaders));
    return settingsRepository.update(existingSetting);
  }

  private UUID getUserId(Map<String, String> okapiHeaders) {
    var userId = okapiHeaders.get(XOkapiHeaders.USER_ID);
    if (userId == null || userId.isEmpty()) {
      logger.warn("User ID not found in Okapi headers, using default value");
      return UUID.fromString("00000000-0000-0000-0000-000000000000");
    }
    return UUID.fromString(userId);
  }

  private void publishSettingEvent(Setting entity, Map<String, String> headers) {
    var event = new SettingEvent(entity.getId().toString(), entity.getKey(), entity.getValue(), headers.get(TENANT));
    publisher.publish(event, entity.getId().toString(), headers)
      .onSuccess(v -> logger.debug("Published setting event for key {} and tenant {}",
        entity.getKey(), headers.get(TENANT)))
      .onFailure(t -> logger.error("Error publishing setting event for key {} and tenant {}",
        entity.getKey(), headers.get(TENANT), t));
  }
}
