package org.folio.rest.impl;

import static org.folio.rest.impl.ElectronicAccessRelationshipApi.ELECTRONIC_ACCESS_RELATIONSHIP_TABLE;

import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.folio.rest.jaxrs.model.ElectronicAccessRelationship;
import org.folio.rest.jaxrs.model.ElectronicAccessRelationships;
import org.folio.rest.jaxrs.model.Metadata;

class ElectronicAccessRelationshipsIT
  extends BaseReferenceDataIntegrationTest<ElectronicAccessRelationship, ElectronicAccessRelationships> {

  @Override
  protected String referenceTable() {
    return ELECTRONIC_ACCESS_RELATIONSHIP_TABLE;
  }

  @Override
  protected String resourceUrl() {
    return "/electronic-access-relationships";
  }

  @Override
  protected Class<ElectronicAccessRelationship> targetClass() {
    return ElectronicAccessRelationship.class;
  }

  @Override
  protected Class<ElectronicAccessRelationships> collectionClass() {
    return ElectronicAccessRelationships.class;
  }

  @Override
  protected ElectronicAccessRelationship sampleRecord() {
    return new ElectronicAccessRelationship().withName("test-type").withSource("test-source");
  }

  @Override
  protected Function<ElectronicAccessRelationships, List<ElectronicAccessRelationship>> collectionRecordsExtractor() {
    return ElectronicAccessRelationships::getElectronicAccessRelationships;
  }

  @Override
  protected List<Function<ElectronicAccessRelationship, Object>> recordFieldExtractors() {
    return List.of(ElectronicAccessRelationship::getName, ElectronicAccessRelationship::getSource);
  }

  @Override
  protected Function<ElectronicAccessRelationship, String> idExtractor() {
    return ElectronicAccessRelationship::getId;
  }

  @Override
  protected Function<ElectronicAccessRelationship, Metadata> metadataExtractor() {
    return ElectronicAccessRelationship::getMetadata;
  }

  @Override
  protected UnaryOperator<ElectronicAccessRelationship> recordModifyingFunction() {
    return relationship -> relationship.withName("name-updated").withSource("source-updated");
  }

  @Override
  protected List<String> queries() {
    return List.of("name==test-type", "source=test-source");
  }
}
