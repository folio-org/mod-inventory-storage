package org.folio.rest.impl;

import io.vertx.junit5.VertxExtension;
import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.folio.rest.jaxrs.model.IdentifierType;
import org.folio.rest.jaxrs.model.IdentifierTypes;
import org.folio.rest.jaxrs.model.Metadata;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
public class IdentifierTypesIT extends BaseReferenceDataIntegrationTest<IdentifierType, IdentifierTypes> {
  @Override
  protected String referenceTable() {
    return "identifier_type";
  }

  @Override
  protected String resourceUrl() {
    return "/identifier-types";
  }

  @Override
  protected Class<IdentifierType> targetClass() {
    return IdentifierType.class;
  }

  @Override
  protected Class<IdentifierTypes> collectionClass() {
    return IdentifierTypes.class;
  }

  @Override
  protected IdentifierType sampleRecord() {
    return new IdentifierType()
      .withName("Sample-Identifier-Type")
      .withSource("Sample-Source");
  }

  @Override
  protected Function<IdentifierTypes, List<IdentifierType>> collectionRecordsExtractor() {
    return IdentifierTypes::getIdentifierTypes;
  }

  @Override
  protected List<Function<IdentifierType, Object>> recordFieldExtractors() {
    return List.of(IdentifierType::getName, IdentifierType::getSource);
  }

  @Override
  protected Function<IdentifierType, String> idExtractor() {
    return IdentifierType::getId;
  }

  @Override
  protected Function<IdentifierType, Metadata> metadataExtractor() {
    return IdentifierType::getMetadata;
  }

  @Override
  protected UnaryOperator<IdentifierType> recordModifyingFunction() {
    return identifierType -> identifierType.withName(identifierType.getName() + "-Updated");
  }

  @Override
  protected List<String> queries() {
    return List.of("name==Sample-Identifier-Type",
      "source==Sample-Source");
  }
}
