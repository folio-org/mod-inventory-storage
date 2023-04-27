package org.folio.services.domainevent;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;

import org.junit.Test;

public class DomainEventRawTest {

  @Test
  public void updateEventIncludesOldAndNewRecordRespresentationsAndEventTypeAndTenant() {
    assertThat(DomainEventRaw.updateEvent("myold", "mynew", "mytenant").toString(),
      endsWith("[oldEntity=myold,newEntity=mynew,type=UPDATE,tenant=mytenant]"));
  }

}
