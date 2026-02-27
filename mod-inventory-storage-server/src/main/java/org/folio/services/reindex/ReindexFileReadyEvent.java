package org.folio.services.reindex;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * Payload for the {@code INVENTORY_REINDEX_FILE_READY} Kafka event.
 * Emitted after a single NDJSON range file has been successfully written to S3.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ReindexFileReadyEvent {

  private final String tenantId;
  private final String recordType;
  private final Range range;
  private final String rangeId;
  private final String traceId;
  private final String bucket;
  private final String objectKey;
  private final String createdDate;

  private ReindexFileReadyEvent(Builder builder) {
    this.tenantId = builder.tenantId;
    this.recordType = builder.recordType;
    this.range = builder.range;
    this.rangeId = builder.rangeId;
    this.traceId = builder.traceId;
    this.bucket = builder.bucket;
    this.objectKey = builder.objectKey;
    this.createdDate = builder.createdDate;
  }

  public static Builder builder() {
    return new Builder();
  }

  public String getTenantId() {
    return tenantId;
  }

  public String getRecordType() {
    return recordType;
  }

  public Range getRange() {
    return range;
  }

  public String getRangeId() {
    return rangeId;
  }

  public String getTraceId() {
    return traceId;
  }

  public String getBucket() {
    return bucket;
  }

  public String getObjectKey() {
    return objectKey;
  }

  public String getCreatedDate() {
    return createdDate;
  }

  public static final class Builder {

    private final String createdDate = Instant.now().toString();

    private String tenantId;
    private String recordType;
    private Range range;
    private String rangeId;
    private String traceId;
    private String bucket;
    private String objectKey;

    private Builder() { }

    public Builder tenantId(String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Builder recordType(String recordType) {
      this.recordType = recordType;
      return this;
    }

    public Builder range(String from, String to) {
      this.range = new Range(from, to);
      return this;
    }

    public Builder rangeId(String rangeId) {
      this.rangeId = rangeId;
      return this;
    }

    public Builder traceId(String traceId) {
      this.traceId = traceId;
      return this;
    }

    public Builder bucket(String bucket) {
      this.bucket = bucket;
      return this;
    }

    public Builder objectKey(String objectKey) {
      this.objectKey = objectKey;
      return this;
    }

    public ReindexFileReadyEvent build() {
      return new ReindexFileReadyEvent(this);
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Range(String from, String to) { }
}
