package org.folio.rest.impl;

import static org.folio.rest.impl.StatisticalCodeTypeApi.STATISTICAL_CODE_TYPE_TABLE;

import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.jaxrs.model.StatisticalCodeType;
import org.folio.rest.jaxrs.model.StatisticalCodeTypes;

class StatisticalCodeTypesIT extends BaseReferenceDataIntegrationTest<StatisticalCodeType, StatisticalCodeTypes> {

  @Override
  protected String referenceTable() {
    return STATISTICAL_CODE_TYPE_TABLE;
  }

  @Override
  protected String resourceUrl() {
    return "/statistical-code-types";
  }

  @Override
  protected Class<StatisticalCodeType> targetClass() {
    return StatisticalCodeType.class;
  }

  @Override
  protected Class<StatisticalCodeTypes> collectionClass() {
    return StatisticalCodeTypes.class;
  }

  @Override
  protected StatisticalCodeType sampleRecord() {
    return new StatisticalCodeType().withName("test-type").withSource("test-source");
  }

  @Override
  protected Function<StatisticalCodeTypes, List<StatisticalCodeType>> collectionRecordsExtractor() {
    return StatisticalCodeTypes::getStatisticalCodeTypes;
  }

  @Override
  protected List<Function<StatisticalCodeType, Object>> recordFieldExtractors() {
    return List.of(StatisticalCodeType::getName, StatisticalCodeType::getSource);
  }

  @Override
  protected Function<StatisticalCodeType, String> idExtractor() {
    return StatisticalCodeType::getId;
  }

  @Override
  protected Function<StatisticalCodeType, Metadata> metadataExtractor() {
    return StatisticalCodeType::getMetadata;
  }

  @Override
  protected UnaryOperator<StatisticalCodeType> recordModifyingFunction() {
    return codeType -> codeType.withName("name-updated").withSource("source-updated");
  }

  @Override
  protected List<String> queries() {
    return List.of("name==test-type", "source=test-source");
  }
}
