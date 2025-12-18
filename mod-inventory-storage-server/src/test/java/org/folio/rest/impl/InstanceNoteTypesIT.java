package org.folio.rest.impl;

import static org.folio.rest.impl.InstanceNoteTypeApi.INSTANCE_NOTE_TYPE_TABLE;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.folio.rest.jaxrs.model.InstanceNoteType;
import org.folio.rest.jaxrs.model.InstanceNoteTypes;
import org.folio.rest.jaxrs.model.Metadata;

public class InstanceNoteTypesIT extends BaseReferenceDataIntegrationTest<InstanceNoteType, InstanceNoteTypes> {

  @Override
  protected String referenceTable() {
    return INSTANCE_NOTE_TYPE_TABLE;
  }

  @Override
  protected String resourceUrl() {
    return "/instance-note-types";
  }

  @Override
  protected Class<InstanceNoteType> targetClass() {
    return InstanceNoteType.class;
  }

  @Override
  protected Class<InstanceNoteTypes> collectionClass() {
    return InstanceNoteTypes.class;
  }

  @Override
  protected InstanceNoteType sampleRecord() {
    return new InstanceNoteType()
      .withId(UUID.randomUUID().toString())
      .withName("name")
      .withSource("source");
  }

  @Override
  protected Function<InstanceNoteTypes, List<InstanceNoteType>> collectionRecordsExtractor() {
    return InstanceNoteTypes::getInstanceNoteTypes;
  }

  @Override
  protected List<Function<InstanceNoteType, Object>> recordFieldExtractors() {
    return List.of(
      InstanceNoteType::getName,
      InstanceNoteType::getSource
    );
  }

  @Override
  protected Function<InstanceNoteType, String> idExtractor() {
    return InstanceNoteType::getId;
  }

  @Override
  protected Function<InstanceNoteType, Metadata> metadataExtractor() {
    return InstanceNoteType::getMetadata;
  }

  @Override
  protected UnaryOperator<InstanceNoteType> recordModifyingFunction() {
    return instanceNoteType -> instanceNoteType.withName("updated");
  }

  @Override
  protected List<String> queries() {
    return List.of("name==name", "source==source");
  }
}
