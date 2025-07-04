package org.folio.rest.impl;

import static org.folio.rest.impl.InstanceFormatApi.INSTANCE_FORMAT_TABLE;

import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.folio.rest.jaxrs.model.InstanceFormat;
import org.folio.rest.jaxrs.model.InstanceFormats;
import org.folio.rest.jaxrs.model.Metadata;

class InstanceFormatsIT extends BaseReferenceDataIntegrationTest<InstanceFormat, InstanceFormats> {

  @Override
  protected String referenceTable() {
    return INSTANCE_FORMAT_TABLE;
  }

  @Override
  protected String resourceUrl() {
    return "/instance-formats";
  }

  @Override
  protected Class<InstanceFormat> targetClass() {
    return InstanceFormat.class;
  }

  @Override
  protected Class<InstanceFormats> collectionClass() {
    return InstanceFormats.class;
  }

  @Override
  protected InstanceFormat sampleRecord() {
    return new InstanceFormat().withName("test-format").withCode("frmt-code").withSource("test-source");
  }

  @Override
  protected Function<InstanceFormats, List<InstanceFormat>> collectionRecordsExtractor() {
    return InstanceFormats::getInstanceFormats;
  }

  @Override
  protected List<Function<InstanceFormat, Object>> recordFieldExtractors() {
    return List.of(InstanceFormat::getName, InstanceFormat::getSource);
  }

  @Override
  protected Function<InstanceFormat, String> idExtractor() {
    return InstanceFormat::getId;
  }

  @Override
  protected Function<InstanceFormat, Metadata> metadataExtractor() {
    return InstanceFormat::getMetadata;
  }

  @Override
  protected UnaryOperator<InstanceFormat> recordModifyingFunction() {
    return instanceFormat -> instanceFormat.withName("name-updated").withSource("source-updated");
  }

  @Override
  protected List<String> queries() {
    return List.of("name==test-format", "source=test-source");
  }
}
