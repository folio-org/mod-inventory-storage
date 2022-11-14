package org.folio.services;

import org.folio.services.domainevent.CommonDomainEventPublisherTest;
import org.folio.services.domainevent.DomainEventRawTest;
import org.folio.services.domainevent.LogToDbFailureHandlerTest;
import org.folio.services.instance.InstanceEffectiveValuesServiceTest;
import org.folio.services.instance.PublicationPeriodParserTest;
import org.folio.services.iteration.IterationServiceTest;
import org.folio.services.kafka.topic.KafkaAdminClientServiceTest;
import org.folio.services.kafka.topic.KafkaTopicsExistsTest;
import org.folio.services.migration.BatchedReadStreamTest;
import org.folio.services.migration.item.ItemShelvingOrderMigrationServiceTest;
import org.folio.services.reindex.ReindexServiceTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
  BatchedReadStreamTest.class,
  CallNumberUtilsTest.class,
  CommonDomainEventPublisherTest.class,
  DomainEventRawTest.class,
  InstanceEffectiveValuesServiceTest.class,
  ItemShelvingOrderMigrationServiceTest.class,
  IterationServiceTest.class,
  KafkaAdminClientServiceTest.class,
  KafkaTopicsExistsTest.class,
  LogToDbFailureHandlerTest.class,
  PublicationPeriodParserTest.class,
  ReindexServiceTest.class,
})
public class ServiceTestSuite {

  private ServiceTestSuite() {
    throw new UnsupportedOperationException("Cannot instantiate utility class.");
  }
}
