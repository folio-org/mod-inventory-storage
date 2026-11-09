package org.folio.services.domainevent;

import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;
import static org.folio.rest.tools.utils.TenantTool.tenantId;

import io.vertx.kafka.client.producer.KafkaProducerRecord;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import org.folio.persist.NotificationSendingErrorRepository;
import org.folio.persist.entity.NotificationSendingError;
import org.springframework.stereotype.Component;

@Component
public final class LogToDbFailureHandler implements FailureHandler {
  private final NotificationSendingErrorRepository repository;

  public LogToDbFailureHandler(NotificationSendingErrorRepository repository) {
    this.repository = repository;
  }

  @Override
  public void handleFailure(Throwable error, KafkaProducerRecord<String, String> producerRecord,
                            Map<String, String> headers) {
    var errorLog = new NotificationSendingError(UUID.randomUUID().toString(),
      producerRecord.topic(), producerRecord.key(), producerRecord.value(),
      getStackTrace(error), new Date());

    repository.save(errorLog.getId(), errorLog, tenantId(headers));
  }
}
