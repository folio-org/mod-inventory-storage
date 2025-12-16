package org.folio.rest.impl;

import io.vertx.junit5.VertxExtension;
import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.folio.rest.jaxrs.model.InstanceRelationshipType;
import org.folio.rest.jaxrs.model.InstanceRelationshipTypes;
import org.folio.rest.jaxrs.model.Metadata;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
class InstanceRelationshipTypesIT
  extends BaseReferenceDataIntegrationTest<InstanceRelationshipType, InstanceRelationshipTypes> {

  @Override
  protected String referenceTable() {
    return "instance_relationship_type";
  }

  @Override
  protected String resourceUrl() {
    return "/instance-relationship-types";
  }

  @Override
  protected Class<InstanceRelationshipType> targetClass() {
    return InstanceRelationshipType.class;
  }

  @Override
  protected Class<InstanceRelationshipTypes> collectionClass() {
    return InstanceRelationshipTypes.class;
  }

  @Override
  protected InstanceRelationshipType sampleRecord() {
    return new InstanceRelationshipType()
      .withName("Sample-Instance-Relationship-Type");
  }

  @Override
  protected Function<InstanceRelationshipTypes, List<InstanceRelationshipType>> collectionRecordsExtractor() {
    return InstanceRelationshipTypes::getInstanceRelationshipTypes;
  }

  @Override
  protected List<Function<InstanceRelationshipType, Object>> recordFieldExtractors() {
    return List.of(InstanceRelationshipType::getName);
  }

  @Override
  protected Function<InstanceRelationshipType, String> idExtractor() {
    return InstanceRelationshipType::getId;
  }

  @Override
  protected Function<InstanceRelationshipType, Metadata> metadataExtractor() {
    return InstanceRelationshipType::getMetadata;
  }

  @Override
  protected UnaryOperator<InstanceRelationshipType> recordModifyingFunction() {
    return type -> type.withName(type.getName() + "-Updated");
  }

  @Override
  protected List<String> queries() {
    return List.of("name==Sample-Instance-Relationship-Type");
  }
}
