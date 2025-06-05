package org.folio.rest.impl;

import static org.folio.services.classification.ClassificationTypeService.CLASSIFICATION_TYPE_TABLE;

import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.folio.rest.jaxrs.model.ClassificationType;
import org.folio.rest.jaxrs.model.ClassificationTypes;
import org.folio.rest.jaxrs.model.Metadata;

class ClassificationTypesIT extends BaseReferenceDataIntegrationTest<ClassificationType, ClassificationTypes> {

  @Override
  protected String referenceTable() {
    return CLASSIFICATION_TYPE_TABLE;
  }

  @Override
  protected String resourceUrl() {
    return "/classification-types";
  }

  @Override
  protected Class<ClassificationType> targetClass() {
    return ClassificationType.class;
  }

  @Override
  protected Class<ClassificationTypes> collectionClass() {
    return ClassificationTypes.class;
  }

  @Override
  protected ClassificationType sampleRecord() {
    return new ClassificationType().withName("test-type").withSource("test-source");
  }

  @Override
  protected Function<ClassificationTypes, List<ClassificationType>> collectionRecordsExtractor() {
    return ClassificationTypes::getClassificationTypes;
  }

  @Override
  protected List<Function<ClassificationType, Object>> recordFieldExtractors() {
    return List.of(ClassificationType::getName, ClassificationType::getSource);
  }

  @Override
  protected Function<ClassificationType, String> idExtractor() {
    return ClassificationType::getId;
  }

  @Override
  protected Function<ClassificationType, Metadata> metadataExtractor() {
    return ClassificationType::getMetadata;
  }

  @Override
  protected UnaryOperator<ClassificationType> recordModifyingFunction() {
    return classificationType -> classificationType.withName("name-updated").withSource("source-updated");
  }

  @Override
  protected List<String> queries() {
    return List.of("name==test-type", "source=test-source");
  }
}
