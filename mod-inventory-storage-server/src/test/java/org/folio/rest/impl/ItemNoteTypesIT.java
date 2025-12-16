package org.folio.rest.impl;

import static org.folio.rest.impl.ItemNoteTypeApi.ITEM_NOTE_TYPE_TABLE;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.folio.rest.jaxrs.model.ItemNoteType;
import org.folio.rest.jaxrs.model.ItemNoteTypes;
import org.folio.rest.jaxrs.model.Metadata;

public class ItemNoteTypesIT extends BaseReferenceDataIntegrationTest<ItemNoteType, ItemNoteTypes> {

  @Override
  protected String referenceTable() {
    return ITEM_NOTE_TYPE_TABLE;
  }

  @Override
  protected String resourceUrl() {
    return "/item-note-types";
  }

  @Override
  protected Class<ItemNoteType> targetClass() {
    return ItemNoteType.class;
  }

  @Override
  protected Class<ItemNoteTypes> collectionClass() {
    return ItemNoteTypes.class;
  }

  @Override
  protected ItemNoteType sampleRecord() {
    return new ItemNoteType()
      .withId(UUID.randomUUID().toString())
      .withName("name")
      .withSource("source");
  }

  @Override
  protected Function<ItemNoteTypes, List<ItemNoteType>> collectionRecordsExtractor() {
    return ItemNoteTypes::getItemNoteTypes;
  }

  @Override
  protected List<Function<ItemNoteType, Object>> recordFieldExtractors() {
    return List.of(
      ItemNoteType::getName,
      ItemNoteType::getSource
    );
  }

  @Override
  protected Function<ItemNoteType, String> idExtractor() {
    return ItemNoteType::getId;
  }

  @Override
  protected Function<ItemNoteType, Metadata> metadataExtractor() {
    return ItemNoteType::getMetadata;
  }

  @Override
  protected UnaryOperator<ItemNoteType> recordModifyingFunction() {
    return itemNoteType -> itemNoteType.withName("modified");
  }

  @Override
  protected List<String> queries() {
    return List.of(
      "name==name",
      "source==source"
    );
  }
}
