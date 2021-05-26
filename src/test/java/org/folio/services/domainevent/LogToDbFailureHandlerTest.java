package org.folio.services.domainevent;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import io.vertx.kafka.client.producer.impl.KafkaProducerRecordImpl;
import java.util.Date;
import org.folio.persist.NotificationSendingErrorRepository;
import org.folio.persist.entity.NotificationSendingError;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class LogToDbFailureHandlerTest {
  @Mock
  private NotificationSendingErrorRepository repository;

  @Test
  public void canHandleFailure() {
    var handler = new LogToDbFailureHandler(repository);
    handler.handleFailure(new IllegalArgumentException("null"),
      new KafkaProducerRecordImpl<>("topic", "key", "value"));

    var errorArgumentCaptor = ArgumentCaptor.forClass(NotificationSendingError.class);
    verify(repository).save(any(), errorArgumentCaptor.capture());

    var notificationSendingError = errorArgumentCaptor.getValue();
    assertThat(notificationSendingError.getId(), notNullValue());
    assertThat(notificationSendingError.getTopicName(), is("topic"));
    assertThat(notificationSendingError.getPartitionKey(), is("key"));
    assertThat(notificationSendingError.getPayload(), is("value"));
    assertThat(notificationSendingError.getError(),
      containsString("IllegalArgumentException: null"));
    assertThat(notificationSendingError.getIncidentDateTime()
      .before(new Date()), is(true));
  }
}
