package org.folio.rest.impl;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.jaxrs.model.SubjectType;
import org.folio.rest.jaxrs.model.SubjectTypes;
import org.folio.services.subjecttype.SubjectTypeService;

public class SubjectTypesIT extends BaseReferenceDataIntegrationTest<SubjectType, SubjectTypes> {

  @Override
  protected String referenceTable() {
    return SubjectTypeService.SUBJECT_TYPE;
  }

  @Override
  protected String resourceUrl() {
    return "/subject-types";
  }

  @Override
  protected Class<SubjectType> targetClass() {
    return SubjectType.class;
  }

  @Override
  protected Class<SubjectTypes> collectionClass() {
    return SubjectTypes.class;
  }

  @Override
  protected SubjectType sampleRecord() {
    return new SubjectType()
      .withId(UUID.randomUUID().toString())
      .withName("Controlled")
      .withSource(SubjectType.Source.LOCAL);
  }

  @Override
  protected Function<SubjectTypes, List<SubjectType>> collectionRecordsExtractor() {
    return SubjectTypes::getSubjectTypes;
  }

  @Override
  protected List<Function<SubjectType, Object>> recordFieldExtractors() {
    return List.of(SubjectType::getName, SubjectType::getSource);
  }

  @Override
  protected Function<SubjectType, String> idExtractor() {
    return SubjectType::getId;
  }

  @Override
  protected Function<SubjectType, Metadata> metadataExtractor() {
    return SubjectType::getMetadata;
  }

  @Override
  protected UnaryOperator<SubjectType> recordModifyingFunction() {
    return subjectType -> subjectType.withName("updated");
  }

  @Override
  protected List<String> queries() {
    return List.of("name==Controlled");
  }
}
