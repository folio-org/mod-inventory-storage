package org.folio.services;

import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import lombok.SneakyThrows;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.InstanceWithoutPubPeriod;
import org.folio.rest.jaxrs.model.Instances;
import org.folio.rest.jaxrs.model.InstancesWithoutPubPeriod;
import org.folio.utils.ObjectConverterUtils;
import org.junit.jupiter.api.Test;

class ObjectConverterUtilsTest {

  private static final String TITLE = "title";
  private static final String ID = "123456789";

  @Test
  void shouldConvertToInstances() {
    var instanceWithoutPubPeriod = new InstanceWithoutPubPeriod();
    instanceWithoutPubPeriod.setId(ID);
    instanceWithoutPubPeriod.setTitle(TITLE);
    var instancesWithoutPubPeriod = new InstancesWithoutPubPeriod();
    instancesWithoutPubPeriod.setInstances(List.of(instanceWithoutPubPeriod));

    var result = ObjectConverterUtils.convertObject(instancesWithoutPubPeriod, Instances.class);
    var instances = result.getInstances();

    assertNotNull(instances);
    assertEquals(1, instances.size());
    assertEquals(instanceWithoutPubPeriod.getId(), instances.get(0).getId());
    assertEquals(instanceWithoutPubPeriod.getTitle(), instances.get(0).getTitle());
  }

  @Test
  void shouldConvertToInstance() {
    var instanceWithoutPubPeriod = new InstanceWithoutPubPeriod();
    instanceWithoutPubPeriod.setId(ID);
    instanceWithoutPubPeriod.setTitle(TITLE);

    var result = ObjectConverterUtils.convertObject(instanceWithoutPubPeriod, Instance.class);

    assertNotNull(result);
    assertEquals(instanceWithoutPubPeriod.getId(), result.getId());
    assertEquals(instanceWithoutPubPeriod.getTitle(), result.getTitle());
  }

  @Test
  @SneakyThrows
  void shouldThrowExceptionWhenCannotConvertToInstance() {
    assertThrows(Exception.class, () -> ObjectConverterUtils.convertObject(new Object(), String.class));
  }
}
