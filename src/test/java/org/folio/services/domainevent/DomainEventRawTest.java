package org.folio.services.domainevent;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;

import org.junit.Test;

public class DomainEventRawTest {

  @Test
  public void deleteEventIncludesOldRecordRespresentationAndEventTypeAndTenant() {
    assertThat(DomainEventRaw.deleteEvent("myold", "mytenant").toString(),
      endsWith("[oldEntity=myold,newEntity=<null>,type=DELETE,tenant=mytenant]"));
  }
}
