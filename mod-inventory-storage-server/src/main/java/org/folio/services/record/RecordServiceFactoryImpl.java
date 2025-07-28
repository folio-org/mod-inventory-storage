package org.folio.services.record;

import io.vertx.core.Vertx;
import java.util.Map;
import org.folio.service.RecordService;
import org.folio.service.RecordServiceImpl;
import org.folio.service.spi.RecordServiceFactory;

public class RecordServiceFactoryImpl implements RecordServiceFactory {

  @Override
  public RecordService create(Vertx vertx) {
    return RecordServiceImpl.createForSingleTable(
      vertx,
      Map.of("item", "item"));
  }
}
