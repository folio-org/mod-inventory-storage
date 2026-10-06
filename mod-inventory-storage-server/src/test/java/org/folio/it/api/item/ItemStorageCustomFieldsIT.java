package org.folio.it.api.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.HttpStatus.SC_CREATED;
import static org.folio.HttpStatus.SC_NO_CONTENT;
import static org.folio.HttpStatus.SC_OK;
import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Custom field definitions ({@code /custom-fields}, provided by folio-custom-fields) and the
 * {@code customFields} values stored on items: a definition change cascades to every item that
 * references the field.
 */
class ItemStorageCustomFieldsIT extends ItemStorageTestBase {

  private static final String CUSTOM_FIELDS = "customFields";
  private static final String CUSTOM_FIELDS_PATH = "/custom-fields";
  private static final String ENTITY_TYPE_ITEM = "item";
  private static final String TEXTBOX = "textbox";
  private static final String SINGLE_SELECT = "singleselect";
  private static final String MULTI_SELECT = "multiselect";

  private String holdingId;
  private JsonObject textboxField;
  private JsonObject singleSelectField;
  private JsonObject multiSelectField;

  @BeforeEach
  void setUp() {
    runQuery("TRUNCATE TABLE custom_fields");
    holdingId = createHoldingRecord();
    textboxField = customField(TEXTBOX, "TEXTBOX_SHORT", null);
    singleSelectField = customField(SINGLE_SELECT, "SINGLE_SELECT_DROPDOWN", selectField(3, false));
    multiSelectField = customField(MULTI_SELECT, "MULTI_SELECT_DROPDOWN", selectField(4, true));
  }

  @Test
  @DisplayName("should count referencing items in the field and option statistics")
  void shouldCountReferencingItems_inFieldAndOptionStatistics() {
    createCustomField(textboxField);
    createCustomField(singleSelectField);
    createCustomField(multiSelectField);

    createItem(itemWithCustomFields(allValues()));
    createItem(itemWithCustomFields(allValues()));
    createItem(itemWithCustomFields(allValues()));

    assertThat(fieldStatisticCount(singleSelectField)).isEqualTo(3);
    assertThat(optionStatisticCount(singleSelectField, "opt_0")).isEqualTo(3);
  }

  @Test
  @DisplayName("should remove the value from the item when the custom field is deleted")
  void shouldRemoveValueFromItem_whenCustomFieldIsDeleted() {
    createCustomField(textboxField);
    var item = createItem(itemWithCustomFields(new JsonObject().put(TEXTBOX, "text1")));
    assertThat(customFieldsOf(item).containsKey(TEXTBOX)).isTrue();
    assertThat(fieldStatisticCount(textboxField)).isEqualTo(1);

    var response = await(doDelete(client, CUSTOM_FIELDS_PATH + "/" + id(textboxField)));

    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
    assertThat(customFieldsCount()).isZero();
    assertThat(customFieldsOf(getItemJsonById(item.getString("id"))).containsKey(TEXTBOX)).isFalse();
  }

  @Test
  @DisplayName("should drop removed options from the item when the custom field is updated")
  void shouldDropRemovedOptionsFromItem_whenCustomFieldIsUpdated() {
    createCustomField(multiSelectField);
    var values = new JsonObject().put(MULTI_SELECT, new JsonArray().add("opt_1").add("opt_2"));
    var item = createItem(itemWithCustomFields(values));
    assertThat(multiSelectValues(item)).containsExactly("opt_1", "opt_2");

    multiSelectField.put("selectField", selectField(2, true));
    var response = await(doPut(client, CUSTOM_FIELDS_PATH + "/" + id(multiSelectField), multiSelectField));

    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
    assertThat(multiSelectValues(getItemJsonById(item.getString("id")))).containsExactly("opt_1");
  }

  @Test
  @DisplayName("should remove values of omitted fields when the custom field collection is replaced")
  void shouldRemoveValuesOfOmittedFields_whenCustomFieldCollectionIsReplaced() {
    createCustomField(textboxField);
    createCustomField(singleSelectField);
    createCustomField(multiSelectField);
    var first = createItem(itemWithCustomFields(allValues()));
    var second = createItem(itemWithCustomFields(allValues()));
    assertThat(customFieldsOf(first).fieldNames()).containsExactlyInAnyOrder(TEXTBOX, SINGLE_SELECT, MULTI_SELECT);
    assertThat(customFieldsOf(second).fieldNames()).containsExactlyInAnyOrder(TEXTBOX, SINGLE_SELECT, MULTI_SELECT);

    var replacement = customField("newMultiselect", "MULTI_SELECT_DROPDOWN", selectField(4, true));
    var collection = new JsonObject()
      .put(CUSTOM_FIELDS, new JsonArray().add(replacement).add(textboxField).add(singleSelectField))
      .put("entityType", ENTITY_TYPE_ITEM);
    var response = await(doPut(client, CUSTOM_FIELDS_PATH, collection));

    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
    assertThat(customFieldsOf(getItemJsonById(first.getString("id"))).fieldNames())
      .containsExactlyInAnyOrder(TEXTBOX, SINGLE_SELECT);
    assertThat(customFieldsOf(getItemJsonById(second.getString("id"))).fieldNames())
      .containsExactlyInAnyOrder(TEXTBOX, SINGLE_SELECT);
  }

  private JsonObject itemWithCustomFields(JsonObject values) {
    return minimalItemRequest(UUID.randomUUID(), holdingId).put(CUSTOM_FIELDS, values);
  }

  private static JsonObject allValues() {
    return new JsonObject()
      .put(TEXTBOX, "text1")
      .put(SINGLE_SELECT, "opt_0")
      .put(MULTI_SELECT, new JsonArray().add("opt_1").add("opt_2"));
  }

  private static JsonObject customField(String name, String type, JsonObject selectField) {
    var field = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("name", name)
      .put("type", type)
      .put("entityType", ENTITY_TYPE_ITEM);
    if (selectField != null) {
      field.put("selectField", selectField);
    }
    return field;
  }

  private static JsonObject selectField(int numberOfOptions, boolean multiSelect) {
    var values = new JsonArray();
    for (int i = 0; i < numberOfOptions; i++) {
      values.add(new JsonObject().put("id", "opt_" + i).put("value", "opt" + i));
    }
    return new JsonObject()
      .put("multiSelect", multiSelect)
      .put("options", new JsonObject().put("values", values));
  }

  private static void createCustomField(JsonObject field) {
    var response = await(doPost(client, CUSTOM_FIELDS_PATH, field));
    assertThat(response.status()).isEqualTo(SC_CREATED);
  }

  private static String id(JsonObject field) {
    return field.getString("id");
  }

  private static JsonObject customFieldsOf(JsonObject item) {
    return item.getJsonObject(CUSTOM_FIELDS, new JsonObject());
  }

  private static List<Object> multiSelectValues(JsonObject item) {
    return customFieldsOf(item).getJsonArray(MULTI_SELECT).getList();
  }

  private static int customFieldsCount() {
    var response = await(doGet(client, CUSTOM_FIELDS_PATH));
    assertThat(response.status()).isEqualTo(SC_OK);
    return response.jsonBody().getInteger("totalRecords");
  }

  private static int fieldStatisticCount(JsonObject field) {
    return statisticCount(CUSTOM_FIELDS_PATH + "/" + id(field) + "/stats");
  }

  private static int optionStatisticCount(JsonObject field, String optionId) {
    return statisticCount(CUSTOM_FIELDS_PATH + "/" + id(field) + "/options/" + optionId + "/stats");
  }

  private static int statisticCount(String path) {
    var response = await(doGet(client, path));
    assertThat(response.status()).isEqualTo(SC_OK);
    return response.jsonBody().getInteger("count");
  }
}
