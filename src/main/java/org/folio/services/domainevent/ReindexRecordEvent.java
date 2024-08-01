package org.folio.services.domainevent;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.folio.rest.jaxrs.model.PublishReindexRecords.RecordType;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReindexRecordEvent<T> {

  private final List<T> records;
  private final RecordType recordType;
  private final String tenant;


  public ReindexRecordEvent(@JsonProperty("records") List<T> records,
                            @JsonProperty("recordType") RecordType recordType,
                            @JsonProperty("tenant") String tenant) {
    this.records = records;
    this.recordType = recordType;
    this.tenant = tenant;
  }

  public static <T> ReindexRecordEvent<T> reindexEvent(String tenant,
                                                       RecordType recordType,
                                                       List<T> records) {
    return new ReindexRecordEvent<>(records, recordType, tenant);
  }

  @Override
  public String toString() {
    return "ReindexRecordEvent{"
      + "records=" + records
      + ", recordType=" + recordType
      + ", tenant='" + tenant + '\''
      + '}';
  }
}
