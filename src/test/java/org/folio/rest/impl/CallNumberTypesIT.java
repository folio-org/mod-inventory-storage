package org.folio.rest.impl;

import static org.folio.services.callnumber.CallNumberTypeService.CALL_NUMBER_TYPE_TABLE;

import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.folio.rest.jaxrs.model.CallNumberType;
import org.folio.rest.jaxrs.model.CallNumberTypes;
import org.folio.rest.jaxrs.model.Metadata;

class CallNumberTypesIT extends BaseReferenceDataIntegrationTest<CallNumberType, CallNumberTypes> {

  @Override
  protected String referenceTable() {
    return CALL_NUMBER_TYPE_TABLE;
  }

  @Override
  protected String resourceUrl() {
    return "/call-number-types";
  }

  @Override
  protected Class<CallNumberType> targetClass() {
    return CallNumberType.class;
  }

  @Override
  protected Class<CallNumberTypes> collectionClass() {
    return CallNumberTypes.class;
  }

  @Override
  protected CallNumberType sampleRecord() {
    return new CallNumberType().withName("test-type").withSource("test-source");
  }

  @Override
  protected Function<CallNumberTypes, List<CallNumberType>> collectionRecordsExtractor() {
    return CallNumberTypes::getCallNumberTypes;
  }

  @Override
  protected List<Function<CallNumberType, Object>> recordFieldExtractors() {
    return List.of(CallNumberType::getName, CallNumberType::getSource);
  }

  @Override
  protected Function<CallNumberType, String> idExtractor() {
    return CallNumberType::getId;
  }

  @Override
  protected Function<CallNumberType, Metadata> metadataExtractor() {
    return CallNumberType::getMetadata;
  }

  @Override
  protected UnaryOperator<CallNumberType> recordModifyingFunction() {
    return callNumberType -> callNumberType.withName("name-updated").withSource("source-updated");
  }

  @Override
  protected List<String> queries() {
    return List.of("name==test-type", "source=test-source");
  }

}
