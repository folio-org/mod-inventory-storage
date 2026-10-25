package org.folio.it.api.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.HttpStatus.SC_BAD_REQUEST;
import static org.folio.HttpStatus.SC_CONFLICT;
import static org.folio.HttpStatus.SC_CREATED;
import static org.folio.HttpStatus.SC_NO_CONTENT;
import static org.folio.HttpStatus.SC_OK;
import static org.folio.HttpStatus.SC_UNPROCESSABLE_CONTENT;
import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.folio.rest.persist.PostgresClient;
import org.folio.services.customfields.CustomFieldsValueCleanup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Custom field definitions ({@code /custom-fields}, provided by folio-custom-fields) and the
 * {@code customFields} values stored on items: deleting a field returns once the definition is
 * gone, the values are removed in the background, and creating a field that would reuse a refId
 * whose values still exist is refused; removing an option is refused while items hold it.
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
    runQuery("TRUNCATE TABLE custom_fields, custom_fields_cleanup");
    holdingId = createHoldingRecord();
    textboxField = customField(TEXTBOX, "TEXTBOX_SHORT", null);
    singleSelectField = customField(SINGLE_SELECT, "SINGLE_SELECT_DROPDOWN", selectField(3, false));
    multiSelectField = customField(MULTI_SELECT, "MULTI_SELECT_DROPDOWN", selectField(4, true));
  }

  @Test
  @DisplayName("should index the custom fields of items for the statistics and cleanup queries, not per field")
  void shouldIndexCustomFieldsOfItems() {
    createCustomField(textboxField);

    assertThat(indexDefinition("item_customfields_recordservice_idx_gin"))
      .contains("USING gin (((jsonb -> 'customFields'::text)))");
    assertThat(indexNames("item\\_custom\\_fields\\_%")).isEmpty();
  }

  @Test
  @DisplayName("should refuse custom fields of an entity type other than item")
  void shouldRefuseCustomField_whenEntityTypeIsUnsupported() {
    var userField = customField(TEXTBOX, "TEXTBOX_SHORT", null).put("entityType", "user");

    var postResponse = await(doPost(client, CUSTOM_FIELDS_PATH, userField));
    var putResponse = await(doPut(client, CUSTOM_FIELDS_PATH, new JsonObject()
      .put(CUSTOM_FIELDS, new JsonArray().add(userField))
      .put("entityType", "user")));

    assertThat(postResponse.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
    assertThat(postResponse.body().toString()).contains("Unsupported entityType 'user', supported: item");
    assertThat(putResponse.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
    assertThat(customFieldNames()).isEmpty();
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
  @DisplayName("should delete a custom field immediately and remove its item values in the background")
  void shouldDeleteCustomField_andRemoveValuesInBackground() {
    createCustomField(textboxField);
    createCustomField(singleSelectField);
    final var item = createItem(itemWithCustomFields(
      new JsonObject().put(TEXTBOX, "text1").put(SINGLE_SELECT, "opt_0")));

    var response = await(doDelete(client, CUSTOM_FIELDS_PATH + "/" + id(textboxField)));

    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
    assertThat(customFieldNames()).containsExactly(SINGLE_SELECT);
    awaitCleanupDone();
    assertThat(customFieldsOf(getItemJsonById(item.getString("id"))))
      .isEqualTo(new JsonObject().put(SINGLE_SELECT, "opt_0"));
  }

  @Test
  @DisplayName("should refuse saving an item retrieved before the cleanup removed a deleted field's value")
  void shouldRefuseStaleItemSave_afterCleanupRemovedValue() {
    createCustomField(textboxField);
    final var staleItem = createItem(itemWithCustomFields(new JsonObject().put(TEXTBOX, "text1")));

    assertThat(await(doDelete(client, CUSTOM_FIELDS_PATH + "/" + id(textboxField))).status())
      .isEqualTo(SC_NO_CONTENT);
    awaitCleanupDone();
    var response = updateItem(staleItem);

    assertThat(response.status()).isEqualTo(SC_CONFLICT);
    var item = getItemJsonById(staleItem.getString("id"));
    assertThat(item.getInteger("_version")).isEqualTo(staleItem.getInteger("_version") + 1);
    assertThat(customFieldsOf(item)).isEqualTo(new JsonObject());
  }

  @Test
  @DisplayName("should refuse removing an option that items hold on the field and the collection endpoint")
  void shouldRefuseOptionRemoval_whenItemsHoldOption() {
    createCustomField(textboxField);
    createCustomField(singleSelectField);
    createCustomField(multiSelectField);
    final var item = createItem(itemWithCustomFields(allValues()));

    var singleReduced = singleSelectField.copy().put("selectField", new JsonObject()
      .put("multiSelect", false)
      .put("options", new JsonObject().put("values", new JsonArray()
        .add(option("opt_1", "opt1"))
        .add(option("opt_2", "opt2")))));
    var multiReduced = multiSelectField.copy().put("selectField", selectField(2, true));
    var fieldResponse = await(doPut(client, CUSTOM_FIELDS_PATH + "/" + id(singleSelectField), singleReduced));
    var collectionResponse = await(doPut(client, CUSTOM_FIELDS_PATH, collection(singleSelectField, multiReduced)));

    assertThat(fieldResponse.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
    assertThat(fieldResponse.body().toString())
      .contains("Option 'opt0' of custom field 'singleselect' is used by 1 item(s) and cannot be removed");
    assertThat(collectionResponse.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
    assertThat(collectionResponse.body().toString())
      .contains("Option 'opt2' of custom field 'multiselect' is used by 1 item(s) and cannot be removed");
    assertThat(customFieldNames()).containsExactlyInAnyOrder(TEXTBOX, SINGLE_SELECT, MULTI_SELECT);
    assertThat(optionIdsOf(getCustomField(singleSelectField))).containsExactly("opt_0", "opt_1", "opt_2");
    assertThat(optionIdsOf(getCustomField(multiSelectField))).containsExactly("opt_0", "opt_1", "opt_2", "opt_3");
    assertThat(cleanupQueueSize()).isZero();
    assertThat(customFieldsOf(getItemJsonById(item.getString("id")))).isEqualTo(allValues());
  }

  @Test
  @DisplayName("should remove an option that no item holds")
  void shouldRemoveOption_whenNoItemHoldsIt() {
    createCustomField(singleSelectField);
    final var item = createItem(itemWithCustomFields(new JsonObject().put(SINGLE_SELECT, "opt_0")));

    singleSelectField.put("selectField", selectField(2, false));
    var response = await(doPut(client, CUSTOM_FIELDS_PATH + "/" + id(singleSelectField), singleSelectField));

    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
    assertThat(optionIdsOf(getCustomField(singleSelectField))).containsExactly("opt_0", "opt_1");
    assertThat(cleanupQueueSize()).isZero();
    assertThat(customFieldsOf(getItemJsonById(item.getString("id"))))
      .isEqualTo(new JsonObject().put(SINGLE_SELECT, "opt_0"));
  }

  @Test
  @DisplayName("should delete a field in the background and remove an unused option in one collection replacement")
  void shouldReplaceCollection_deletingFieldAndRemovingUnusedOption() {
    createCustomField(textboxField);
    createCustomField(singleSelectField);
    createCustomField(multiSelectField);
    final var item = createItem(itemWithCustomFields(allValues()));

    var reduced = singleSelectField.copy().put("selectField", selectField(2, false));
    var response = await(doPut(client, CUSTOM_FIELDS_PATH, collection(reduced, multiSelectField)));

    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
    assertThat(customFieldNames()).containsExactlyInAnyOrder(SINGLE_SELECT, MULTI_SELECT);
    assertThat(optionIdsOf(getCustomField(singleSelectField))).containsExactly("opt_0", "opt_1");
    awaitCleanupDone();
    assertThat(customFieldsOf(getItemJsonById(item.getString("id")))).isEqualTo(new JsonObject()
      .put(SINGLE_SELECT, "opt_0")
      .put(MULTI_SELECT, new JsonArray().add("opt_1").add("opt_2")));
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1", "x"})
  @DisplayName("should fall back to the default batch size when the configured one is not a positive number")
  void shouldUseDefaultBatchSize_whenConfiguredOneIsInvalid(String batchSize) {
    System.setProperty(CustomFieldsValueCleanup.BATCH_SIZE_KEY, batchSize);
    try {
      createCustomField(textboxField);
      final var item = createItem(itemWithCustomFields(new JsonObject().put(TEXTBOX, "text1")));

      var response = await(doDelete(client, CUSTOM_FIELDS_PATH + "/" + id(textboxField)));

      assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
      awaitCleanupDone();
      assertThat(customFieldsOf(getItemJsonById(item.getString("id")))).isEqualTo(new JsonObject());
    } finally {
      System.clearProperty(CustomFieldsValueCleanup.BATCH_SIZE_KEY);
    }
  }

  @Test
  @DisplayName("should remove the values of a deleted custom field in batches")
  void shouldRemoveValuesInBatches() {
    System.setProperty(CustomFieldsValueCleanup.BATCH_SIZE_KEY, "2");
    try {
      createCustomField(textboxField);
      var items = new ArrayList<String>();
      for (int i = 0; i < 5; i++) {
        items.add(createItem(itemWithCustomFields(new JsonObject().put(TEXTBOX, "text" + i))).getString("id"));
      }

      var response = await(doDelete(client, CUSTOM_FIELDS_PATH + "/" + id(textboxField)));

      assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
      awaitCleanupDone();
      for (String itemId : items) {
        assertThat(customFieldsOf(getItemJsonById(itemId))).isEqualTo(new JsonObject());
      }
    } finally {
      System.clearProperty(CustomFieldsValueCleanup.BATCH_SIZE_KEY);
    }
  }

  @Test
  @DisplayName("should refuse to create a custom field while the values of a deleted field with the same name remain")
  void shouldRefuseToCreateField_whileCleanupIsPending() {
    insertFieldCleanupRow("textbox");
    insertFieldCleanupRow("singleselect_2");

    var sameName = await(doPost(client, CUSTOM_FIELDS_PATH, textboxField));
    var suffixedRefId = await(doPost(client, CUSTOM_FIELDS_PATH, singleSelectField));

    assertThat(sameName.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(sameName.body().toString()).isEqualTo("Custom field 'textbox' cannot be created while the values"
      + " of a deleted custom field with the same name are still being removed");
    assertThat(suffixedRefId.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(customFieldNames()).isEmpty();
  }

  @Test
  @DisplayName("should refuse to delete and recreate a custom field in one collection replacement")
  void shouldRefuseToRecreateField_inOneCollectionReplacement() {
    createCustomField(textboxField);
    final var item = createItem(itemWithCustomFields(new JsonObject().put(TEXTBOX, "text1")));

    var recreated = customField(TEXTBOX, "TEXTBOX_SHORT", null);
    var response = await(doPut(client, CUSTOM_FIELDS_PATH, collection(recreated)));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().toString())
      .isEqualTo("Custom field 'textbox' cannot be deleted and recreated in one request");
    assertThat(customFieldNames()).containsExactly(TEXTBOX);
    assertThat(cleanupQueueSize()).isZero();
    assertThat(customFieldsOf(getItemJsonById(item.getString("id"))).getString(TEXTBOX)).isEqualTo("text1");
  }

  @Test
  @DisplayName("should refuse removing the highest option that items hold while adding one, which would take its id")
  void shouldRefuseRemovingHighestOptionInUse_whileAddingOne() {
    createCustomField(singleSelectField);
    final var item = createItem(itemWithCustomFields(new JsonObject().put(SINGLE_SELECT, "opt_2")));

    var options = new JsonArray()
      .add(option("opt_0", "opt0"))
      .add(option("opt_1", "opt1"))
      .add(new JsonObject().put("value", "new"));
    singleSelectField.put("selectField", new JsonObject()
      .put("multiSelect", false)
      .put("options", new JsonObject().put("values", options)));
    var response = await(doPut(client, CUSTOM_FIELDS_PATH + "/" + id(singleSelectField), singleSelectField));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
    assertThat(optionIdsOf(getCustomField(singleSelectField))).containsExactly("opt_0", "opt_1", "opt_2");
    assertThat(customFieldsOf(getItemJsonById(item.getString("id"))))
      .isEqualTo(new JsonObject().put(SINGLE_SELECT, "opt_2"));
  }

  private static void awaitCleanupDone() {
    Awaitility.await()
      .atMost(Duration.ofSeconds(15))
      .pollInterval(Duration.ofMillis(200))
      .untilAsserted(() -> assertThat(cleanupQueueSize()).isZero());
  }

  private static long cleanupQueueSize() {
    return runQuery("SELECT count(*) FROM custom_fields_cleanup").iterator().next().getLong(0);
  }

  private static void insertFieldCleanupRow(String refId) {
    runQuery("INSERT INTO custom_fields_cleanup (id, entity_type, ref_id) VALUES ('"
      + UUID.randomUUID() + "', 'item', '" + refId + "')");
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

  private static JsonObject option(String id, String value) {
    return new JsonObject().put("id", id).put("value", value);
  }

  private static JsonObject collection(JsonObject... fields) {
    return new JsonObject()
      .put(CUSTOM_FIELDS, new JsonArray(List.of((Object[]) fields)))
      .put("entityType", ENTITY_TYPE_ITEM);
  }

  private static JsonObject createCustomField(JsonObject field) {
    var response = await(doPost(client, CUSTOM_FIELDS_PATH, field));
    assertThat(response.status()).isEqualTo(SC_CREATED);
    return response.jsonBody();
  }

  private static String indexDefinition(String indexName) {
    var rows = runQuery("SELECT indexdef FROM pg_indexes WHERE schemaname = '"
      + PostgresClient.convertToPsqlStandard(TENANT_ID) + "' AND indexname = '" + indexName + "'");
    assertThat(rows.size()).as(indexName).isEqualTo(1);
    return rows.iterator().next().getString("indexdef");
  }

  private static List<String> indexNames(String pattern) {
    var rows = runQuery("SELECT indexname FROM pg_indexes WHERE schemaname = '"
      + PostgresClient.convertToPsqlStandard(TENANT_ID) + "' AND indexname LIKE '" + pattern + "'");
    var names = new ArrayList<String>();
    rows.forEach(row -> names.add(row.getString("indexname")));
    return names;
  }

  private static String id(JsonObject field) {
    return field.getString("id");
  }

  private static JsonObject customFieldsOf(JsonObject item) {
    return item.getJsonObject(CUSTOM_FIELDS, new JsonObject());
  }

  private static List<String> customFieldNames() {
    var response = await(doGet(client, CUSTOM_FIELDS_PATH));
    assertThat(response.status()).isEqualTo(SC_OK);
    return response.jsonBody().getJsonArray(CUSTOM_FIELDS).stream()
      .map(field -> ((JsonObject) field).getString("name"))
      .toList();
  }

  private static JsonObject getCustomField(JsonObject field) {
    var response = await(doGet(client, CUSTOM_FIELDS_PATH + "/" + id(field)));
    assertThat(response.status()).isEqualTo(SC_OK);
    return response.jsonBody();
  }

  private static List<String> optionIdsOf(JsonObject field) {
    return field.getJsonObject("selectField").getJsonObject("options").getJsonArray("values").stream()
      .map(option -> ((JsonObject) option).getString("id"))
      .toList();
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
