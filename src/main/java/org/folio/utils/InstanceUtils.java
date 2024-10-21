package org.folio.utils;

import java.util.List;
import org.apache.commons.beanutils.BeanUtils;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.InstanceWithoutPubPeriod;
import org.folio.rest.jaxrs.model.Instances;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class InstanceUtils {

  private static final Logger log = LoggerFactory.getLogger(InstanceUtils.class);

  private InstanceUtils() {
    throw new UnsupportedOperationException("Utility class");
  }

  public static Instances copyPropertiesToInstances(List<InstanceWithoutPubPeriod> instancesWithoutPubPeriod) {
    var instances = new Instances();
    instances.setInstances(instancesWithoutPubPeriod
      .stream()
      .map(InstanceUtils::copyPropertiesToInstance)
      .toList());
    return instances;
  }

  public static Instance copyPropertiesToInstance(InstanceWithoutPubPeriod instanceWithoutPubPeriod) {
    var instance = new Instance();
    try {
      log.debug("copyPropertiesToInstance:: Copy all fields from InstanceWithoutPubPeriod to Instance, id: '{}'",
        instanceWithoutPubPeriod.getId());
      BeanUtils.copyProperties(instance, instanceWithoutPubPeriod);
      return instance;
    } catch (Exception e) {
      throw new IllegalArgumentException(
        String.format(
          "Failed to copy properties from InstanceWithoutPubPeriod to Instance object: %s", e.getMessage()), e);
    }
  }
}
