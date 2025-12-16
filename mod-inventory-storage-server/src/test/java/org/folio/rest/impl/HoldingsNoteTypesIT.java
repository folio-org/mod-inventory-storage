package org.folio.rest.impl;

import static org.folio.rest.impl.HoldingsNoteTypeApi.HOLDINGS_NOTE_TYPE_TABLE;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.folio.rest.jaxrs.model.HoldingsNoteType;
import org.folio.rest.jaxrs.model.HoldingsNoteTypes;
import org.folio.rest.jaxrs.model.Metadata;

public class HoldingsNoteTypesIT extends BaseReferenceDataIntegrationTest<HoldingsNoteType, HoldingsNoteTypes> {
  @Override
  protected String referenceTable() {
    return HOLDINGS_NOTE_TYPE_TABLE;
  }

  @Override
  protected String resourceUrl() {
    return "/holdings-note-types";
  }

  @Override
  protected Class<HoldingsNoteType> targetClass() {
    return HoldingsNoteType.class;
  }

  @Override
  protected Class<HoldingsNoteTypes> collectionClass() {
    return HoldingsNoteTypes.class;
  }

  @Override
  protected HoldingsNoteType sampleRecord() {
    return new HoldingsNoteType()
      .withId(UUID.randomUUID().toString())
      .withName("name")
      .withSource("source");
  }

  @Override
  protected Function<HoldingsNoteTypes, List<HoldingsNoteType>> collectionRecordsExtractor() {
    return HoldingsNoteTypes::getHoldingsNoteTypes;
  }

  @Override
  protected List<Function<HoldingsNoteType, Object>> recordFieldExtractors() {
    return List.of(
      HoldingsNoteType::getName,
      HoldingsNoteType::getSource
    );
  }

  @Override
  protected Function<HoldingsNoteType, String> idExtractor() {
    return HoldingsNoteType::getId;
  }

  @Override
  protected Function<HoldingsNoteType, Metadata> metadataExtractor() {
    return HoldingsNoteType::getMetadata;
  }

  @Override
  protected UnaryOperator<HoldingsNoteType> recordModifyingFunction() {
    return holdingsNoteType -> holdingsNoteType.withName("Modified");
  }

  @Override
  protected List<String> queries() {
    return List.of(
      "name==name",
      "source==source"
    );
  }
}
