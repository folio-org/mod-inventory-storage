package org.folio.services.record;

import io.vertx.core.Vertx;
import java.util.Map;
import org.folio.service.RecordService;
import org.folio.service.RecordServiceImpl;
import org.folio.service.spi.RecordServiceFactory;
import org.folio.services.customfields.CleanupQueueRecordService;

public class RecordServiceFactoryImpl implements RecordServiceFactory {

  /**
   * Custom field entity types and the tables holding their values; other entity types are refused.
   */
  public static final Map<String, String> ENTITY_TYPE_TABLES = Map.of("item", "item");

  @Override
  public RecordService create(Vertx vertx) {
    return new CleanupQueueRecordService(vertx,
      RecordServiceImpl.createForSingleTable(vertx, ENTITY_TYPE_TABLES));
  }
}
