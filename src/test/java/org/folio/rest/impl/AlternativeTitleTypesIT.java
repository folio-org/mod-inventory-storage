package org.folio.rest.impl;

import static org.folio.rest.impl.AlternativeTitleTypeApi.REFERENCE_TABLE;

import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.folio.rest.jaxrs.model.AlternativeTitleType;
import org.folio.rest.jaxrs.model.AlternativeTitleTypes;
import org.folio.rest.jaxrs.model.Metadata;

class AlternativeTitleTypesIT extends BaseReferenceDataIntegrationTest<AlternativeTitleType, AlternativeTitleTypes> {

  @Override
  protected String referenceTable() {
    return REFERENCE_TABLE;
  }

  @Override
  protected String resourceUrl() {
    return "/alternative-title-types";
  }

  @Override
  protected Class<AlternativeTitleType> targetClass() {
    return AlternativeTitleType.class;
  }

  @Override
  protected Class<AlternativeTitleTypes> collectionClass() {
    return AlternativeTitleTypes.class;
  }

  @Override
  protected AlternativeTitleType sampleRecord() {
    return new AlternativeTitleType().withName("test-type").withSource("test-source");
  }

  @Override
  protected Function<AlternativeTitleTypes, List<AlternativeTitleType>> collectionRecordsExtractor() {
    return AlternativeTitleTypes::getAlternativeTitleTypes;
  }

  @Override
  protected List<Function<AlternativeTitleType, Object>> recordFieldExtractors() {
    return List.of(AlternativeTitleType::getName, AlternativeTitleType::getSource);
  }

  @Override
  protected Function<AlternativeTitleType, String> idExtractor() {
    return AlternativeTitleType::getId;
  }

  @Override
  protected Function<AlternativeTitleType, Metadata> metadataExtractor() {
    return AlternativeTitleType::getMetadata;
  }

  @Override
  protected UnaryOperator<AlternativeTitleType> recordModifyingFunction() {
    return alternativeTitleType -> alternativeTitleType.withName("name-updated").withSource("source-updated");
  }

  @Override
  protected List<String> queries() {
    return List.of("name==test-type", "source=test-source");
  }

}
