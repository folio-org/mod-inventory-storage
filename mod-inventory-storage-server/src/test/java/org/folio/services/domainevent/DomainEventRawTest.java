package org.folio.services.domainevent;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DomainEventRawTest {

  @Test
  void deleteEventIncludesOldRecordRespresentationAndEventTypeAndTenant() {
    assertTrue(DomainEventRaw.deleteEvent("myold", "mytenant").toString()
      .endsWith("[oldEntity=myold,newEntity=<null>,type=DELETE,tenant=mytenant]"));
  }
}
