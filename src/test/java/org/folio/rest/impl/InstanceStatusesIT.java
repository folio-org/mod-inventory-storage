package org.folio.rest.impl;

import static org.folio.rest.impl.InstanceStatusApi.INSTANCE_STATUS_TABLE;

import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.folio.rest.jaxrs.model.InstanceStatus;
import org.folio.rest.jaxrs.model.InstanceStatuses;
import org.folio.rest.jaxrs.model.Metadata;

class InstanceStatusesIT extends BaseReferenceDataIntegrationTest<InstanceStatus, InstanceStatuses> {

  @Override
  protected String referenceTable() {
    return INSTANCE_STATUS_TABLE;
  }

  @Override
  protected String resourceUrl() {
    return "/instance-statuses";
  }

  @Override
  protected Class<InstanceStatus> targetClass() {
    return InstanceStatus.class;
  }

  @Override
  protected Class<InstanceStatuses> collectionClass() {
    return InstanceStatuses.class;
  }

  @Override
  protected InstanceStatus sampleRecord() {
    return new InstanceStatus().withName("test-status").withCode("test-code").withSource("test-source");
  }

  @Override
  protected Function<InstanceStatuses, List<InstanceStatus>> collectionRecordsExtractor() {
    return InstanceStatuses::getInstanceStatuses;
  }

  @Override
  protected List<Function<InstanceStatus, Object>> recordFieldExtractors() {
    return List.of(InstanceStatus::getName, InstanceStatus::getSource);
  }

  @Override
  protected Function<InstanceStatus, String> idExtractor() {
    return InstanceStatus::getId;
  }

  @Override
  protected Function<InstanceStatus, Metadata> metadataExtractor() {
    return InstanceStatus::getMetadata;
  }

  @Override
  protected UnaryOperator<InstanceStatus> recordModifyingFunction() {
    return status -> status.withName("name-updated").withSource("source-updated");
  }

  @Override
  protected List<String> queries() {
    return List.of("name==test-status", "source=test-source");
  }
}
