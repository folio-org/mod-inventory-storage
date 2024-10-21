package org.folio.services;

import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import lombok.SneakyThrows;
import org.folio.rest.jaxrs.model.InstanceWithoutPubPeriod;
import org.folio.utils.InstanceUtils;
import org.junit.jupiter.api.Test;

class InstanceUtilsTest {

  private static final String TITLE = "title";
  private static final String ID = "123456789";

  @Test
  void shouldCopyPropertiesToInstances() {
    var instanceWithoutPubPeriod = new InstanceWithoutPubPeriod();
    instanceWithoutPubPeriod.setId(ID);
    instanceWithoutPubPeriod.setTitle(TITLE);

    var result = InstanceUtils.copyPropertiesToInstances(List.of(instanceWithoutPubPeriod));
    var instances = result.getInstances();

    assertNotNull(instances);
    assertEquals(1, instances.size());
    assertEquals(instanceWithoutPubPeriod.getId(), instances.get(0).getId());
    assertEquals(instanceWithoutPubPeriod.getTitle(), instances.get(0).getTitle());
  }

  @Test
  void shouldCopyPropertiesToInstance() {
    var instanceWithoutPubPeriod = new InstanceWithoutPubPeriod();
    instanceWithoutPubPeriod.setId(ID);
    instanceWithoutPubPeriod.setTitle(TITLE);

    var result = InstanceUtils.copyPropertiesToInstance(instanceWithoutPubPeriod);

    assertNotNull(result);
    assertEquals(instanceWithoutPubPeriod.getId(), result.getId());
    assertEquals(instanceWithoutPubPeriod.getTitle(), result.getTitle());
  }

  @Test
  @SneakyThrows
  void shouldThrowIllegalArgumentExceptionWhenCannotCopyPropertiesToInstance() {
    assertThrows(IllegalArgumentException.class, () -> InstanceUtils.copyPropertiesToInstance(null));
  }
}
