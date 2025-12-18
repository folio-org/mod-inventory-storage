package org.folio.services.domainevent;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.text.IsBlankString.blankOrNullString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import io.vertx.kafka.client.producer.impl.KafkaProducerRecordImpl;
import java.util.Date;
import org.folio.persist.NotificationSendingErrorRepository;
import org.folio.persist.entity.NotificationSendingError;
import org.hamcrest.Matchers;
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
    assertThat(notificationSendingError.getId(), is(not(blankOrNullString())));
    assertThat(notificationSendingError.getTopicName(), is("topic"));
    assertThat(notificationSendingError.getPartitionKey(), is("key"));
    assertThat(notificationSendingError.getPayload(), is("value"));
    assertThat(notificationSendingError.getError(), containsString("IllegalArgumentException: null"));
    assertThat(notificationSendingError.getIncidentDateTime()
      .after(new Date()), Matchers.is(false));
  }
}
