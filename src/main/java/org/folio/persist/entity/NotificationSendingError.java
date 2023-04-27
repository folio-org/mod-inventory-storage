package org.folio.persist.entity;

import java.util.Date;

public class NotificationSendingError {
  private String id;
  private String topicName;
  private String partitionKey;
  private String payload;
  private String error;
  private Date incidentDateTime;

  public NotificationSendingError() { }

  public NotificationSendingError(String id, String topicName, String partitionKey,
                                  String payload, String error, Date incidentDateTime) {

    this.id = id;
    this.topicName = topicName;
    this.partitionKey = partitionKey;
    this.payload = payload;
    this.error = error;
    this.incidentDateTime = incidentDateTime;
  }

  public String getTopicName() {
    return topicName;
  }

  public void setTopicName(String topicName) {
    this.topicName = topicName;
  }

  public String getPartitionKey() {
    return partitionKey;
  }

  public void setPartitionKey(String partitionKey) {
    this.partitionKey = partitionKey;
  }

  public String getPayload() {
    return payload;
  }

  public void setPayload(String payload) {
    this.payload = payload;
  }

  public String getError() {
    return error;
  }

  public void setError(String error) {
    this.error = error;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public Date getIncidentDateTime() {
    return incidentDateTime;
  }

  public void setIncidentDateTime(Date incidentDateTime) {
    this.incidentDateTime = incidentDateTime;
  }
}
