package org.folio.rest.support;

import static org.folio.rest.support.InstanceUtil.copyNonMarcControlledFields;
import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.folio.rest.jaxrs.model.Instance;
import org.junit.Test;

public class InstanceUtilTest {

  @Test
  public void shouldPopulateFieldsNotControlledByMarc() {
    Instance targetInstance = new Instance();
    Instance sourceInstance = new Instance()
      .withDiscoverySuppress(Boolean.TRUE)
      .withStaffSuppress(Boolean.TRUE)
      .withPreviouslyHeld(Boolean.TRUE)
      .withCatalogedDate("1970-01-01")
      .withStatusId(UUID.randomUUID().toString())
      .withStatusUpdatedDate("1970-01-01T12:07:47.602+0000")
      .withStatisticalCodeIds(Set.of(UUID.randomUUID().toString()))
      .withAdministrativeNotes(List.of("test-note1", "test-note2"))
      .withNatureOfContentTermIds(Set.of(UUID.randomUUID().toString()));

    copyNonMarcControlledFields(targetInstance, sourceInstance);

    assertEquals(sourceInstance.getDiscoverySuppress(), targetInstance.getDiscoverySuppress());
    assertEquals(sourceInstance.getStaffSuppress(), targetInstance.getStaffSuppress());
    assertEquals(sourceInstance.getPreviouslyHeld(), targetInstance.getPreviouslyHeld());
    assertEquals(sourceInstance.getCatalogedDate(), targetInstance.getCatalogedDate());
    assertEquals(sourceInstance.getStatusId(), targetInstance.getStatusId());
    assertEquals(sourceInstance.getStatusUpdatedDate(), targetInstance.getStatusUpdatedDate());
    assertEquals(sourceInstance.getStatisticalCodeIds(), targetInstance.getStatisticalCodeIds());
    assertEquals(sourceInstance.getAdministrativeNotes(), targetInstance.getAdministrativeNotes());
    assertEquals(sourceInstance.getNatureOfContentTermIds(), targetInstance.getNatureOfContentTermIds());
  }

}
