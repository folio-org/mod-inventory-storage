package org.folio.it.api.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.HttpStatus.SC_CONFLICT;
import static org.folio.HttpStatus.SC_CREATED;
import static org.folio.HttpStatus.SC_NOT_FOUND;
import static org.folio.HttpStatus.SC_NO_CONTENT;
import static org.folio.HttpStatus.SC_OK;
import static org.folio.HttpStatus.SC_UNPROCESSABLE_CONTENT;
import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgConnection;
import io.vertx.sqlclient.Transaction;
import io.vertx.sqlclient.Tuple;
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
 * gone, the values are removed in the background, and a new field does not take the refId of values
 * that still exist; removing an option is refused while items hold it.
 */
class ItemStorageCustomFieldsIT extends ItemStorageTestBase {

  private static final String CUSTOM_FIELDS = "customFields";
  private static final String CUSTOM_FIELDS_PATH = "/custom-fields";
  private static final String CLEANUP_PATH = "/inventory-storage/custom-fields/cleanup";
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
    assertThat(placeholderCount()).isZero();
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
  @DisplayName("should hide the refId placeholder of a deleted field from the API and number a new field past it")
  void shouldHidePlaceholder_andNumberNewFieldPastIt() {
    var placeholderId = insertPlaceholder(TEXTBOX);

    var byQuery = await(doGet(client, CUSTOM_FIELDS_PATH + "?query=name==" + TEXTBOX));
    var byId = await(doGet(client, CUSTOM_FIELDS_PATH + "/" + placeholderId));
    var statistic = await(doGet(client, CUSTOM_FIELDS_PATH + "/" + placeholderId + "/stats"));
    var optionStatistic = await(doGet(client, CUSTOM_FIELDS_PATH + "/" + placeholderId + "/options/opt_0/stats"));
    var deleted = await(doDelete(client, CUSTOM_FIELDS_PATH + "/" + placeholderId));
    var replaced = await(doPut(client, CUSTOM_FIELDS_PATH, new JsonObject()
      .put(CUSTOM_FIELDS, new JsonArray())
      .put("entityType", CustomFieldsValueCleanup.PLACEHOLDER_ENTITY_TYPE)));
    var created = createCustomField(textboxField);

    assertThat(byQuery.jsonBody().getJsonArray(CUSTOM_FIELDS)).isEmpty();
    assertThat(byId.status()).isEqualTo(SC_NOT_FOUND);
    assertThat(statistic.status()).isEqualTo(SC_NOT_FOUND);
    assertThat(optionStatistic.status()).isEqualTo(SC_NOT_FOUND);
    assertThat(deleted.status()).isEqualTo(SC_NOT_FOUND);
    assertThat(replaced.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
    assertThat(created.getString("refId")).isEqualTo("textbox_2");
    assertThat(customFieldNames()).containsExactly(TEXTBOX);
    assertThat(placeholderCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("should give a field deleted and recreated in one collection replacement the next refId")
  void shouldRecreateField_withNextRefId_inOneCollectionReplacement() {
    createCustomField(textboxField);
    final var item = createItem(itemWithCustomFields(new JsonObject().put(TEXTBOX, "text1")));

    var recreated = customField(TEXTBOX, "TEXTBOX_SHORT", null);
    var response = await(doPut(client, CUSTOM_FIELDS_PATH, collection(recreated)));

    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
    assertThat(getCustomField(recreated).getString("refId")).isEqualTo("textbox_2");
    awaitCleanupDone();
    assertThat(customFieldsOf(getItemJsonById(item.getString("id")))).isEqualTo(new JsonObject());
    assertThat(placeholderCount()).isZero();
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

  @Test
  @DisplayName("should queue no value removal when a collection replacement fails after deleting a field")
  void shouldQueueNothing_whenCollectionReplacementFails() {
    createCustomField(textboxField);
    createCustomField(singleSelectField);
    final var item = createItem(itemWithCustomFields(new JsonObject().put(TEXTBOX, "text1")));

    var typeChanged = singleSelectField.copy().put("type", "RADIO_BUTTON");
    var response = await(doPut(client, CUSTOM_FIELDS_PATH, collection(typeChanged)));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
    assertThat(customFieldNames()).containsExactlyInAnyOrder(TEXTBOX, SINGLE_SELECT);
    assertThat(cleanupQueueSize()).isZero();
    assertThat(customFieldsOf(getItemJsonById(item.getString("id"))).getString(TEXTBOX)).isEqualTo("text1");
  }

  @Test
  @DisplayName("should remove queued values when the timer calls the cleanup endpoint")
  void shouldRemoveQueuedValues_whenTimerCallsCleanupEndpoint() {
    final var item = createItem(itemWithCustomFields(new JsonObject().put(TEXTBOX, "text1")));
    insertFieldCleanupRow(TEXTBOX);
    assertThat(cleanupQueueSize()).isEqualTo(1);

    var response = await(doPost(client, CLEANUP_PATH, null));

    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
    awaitCleanupDone();
    assertThat(customFieldsOf(getItemJsonById(item.getString("id")))).isEqualTo(new JsonObject());
  }

  @Test
  @DisplayName("should release the refId placeholder when discarding a queued removal of an unsupported entity type")
  void shouldReleasePlaceholder_whenDiscardingRemovalOfUnsupportedEntityType() {
    insertPlaceholder(TEXTBOX);
    insertFieldCleanupRow("unsupported", TEXTBOX);

    var response = await(doPost(client, CLEANUP_PATH, null));

    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
    awaitCleanupDone();
    assertThat(placeholderCount()).isZero();
    assertThat(createCustomField(textboxField).getString("refId")).isEqualTo(TEXTBOX);
  }

  @Test
  @DisplayName("should keep a failing removal with its error for the next pass and process the rows after it")
  void shouldKeepFailingRemoval_andProcessRowsAfterIt() {
    refuseItemUpdatesRemoving("refused");
    try {
      final var refusedItem = createItem(itemWithCustomFields(new JsonObject().put("refused", "x")));
      final var item = createItem(itemWithCustomFields(new JsonObject().put(TEXTBOX, "text1")));
      insertFieldCleanupRow("refused");
      insertFieldCleanupRow(TEXTBOX);

      var response = await(doPost(client, CLEANUP_PATH, null));

      assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
      awaitCleanupQueueSize(1);
      var kept = runQuery("SELECT ref_id, attempts, last_error, last_attempt_date FROM custom_fields_cleanup")
        .iterator().next();
      assertThat(kept.getString("ref_id")).isEqualTo("refused");
      assertThat(kept.getInteger("attempts")).isEqualTo(1);
      assertThat(kept.getString("last_error")).contains("cleanup refused");
      assertThat(kept.getValue("last_attempt_date")).isNotNull();
      assertThat(customFieldsOf(getItemJsonById(item.getString("id")))).isEqualTo(new JsonObject());
      assertThat(customFieldsOf(getItemJsonById(refusedItem.getString("id"))).getString("refused")).isEqualTo("x");
    } finally {
      runQuery("DROP TRIGGER IF EXISTS fail_cleanup ON item");
      runQuery("DROP FUNCTION IF EXISTS fail_cleanup()");
    }
  }

  @Test
  @DisplayName("should leave the queue to the instance holding the tenant's lock and process it once released")
  void shouldLeaveQueue_toInstanceHoldingCleanupLock() {
    final var item = createItem(itemWithCustomFields(new JsonObject().put(TEXTBOX, "text1")));
    insertFieldCleanupRow(TEXTBOX);
    var otherInstance = openConnection();
    try {
      holdCleanupLock(otherInstance);

      await(doPost(client, CLEANUP_PATH, null));

      Awaitility.await()
        .during(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(cleanupQueueSize()).isEqualTo(1));
      assertThat(customFieldsOf(getItemJsonById(item.getString("id"))).getString(TEXTBOX)).isEqualTo("text1");
    } finally {
      releaseCleanupLock(otherInstance);
      await(otherInstance.close());
    }

    await(doPost(client, CLEANUP_PATH, null));

    awaitCleanupDone();
    assertThat(customFieldsOf(getItemJsonById(item.getString("id")))).isEqualTo(new JsonObject());
  }

  @Test
  @DisplayName("should go on removing values when a concurrent write leaves a batch with no record to update")
  void shouldContinueRemoval_whenConcurrentWriteEmptiesBatch() {
    var batchSize = 2;
    System.setProperty(CustomFieldsValueCleanup.BATCH_SIZE_KEY, String.valueOf(batchSize));
    try {
      createCustomField(textboxField);
      var items = new ArrayList<String>();
      for (int i = 0; i < 5; i++) {
        items.add(createItem(itemWithCustomFields(new JsonObject().put(TEXTBOX, "text" + i))).getString("id"));
      }

      deleteFieldWhileFirstBatchIsWrittenConcurrently(textboxField, TEXTBOX, batchSize);

      awaitCleanupDone();
      for (String itemId : items) {
        assertThat(customFieldsOf(getItemJsonById(itemId))).isEqualTo(new JsonObject());
      }
      assertThat(placeholderCount()).isZero();
    } finally {
      System.clearProperty(CustomFieldsValueCleanup.BATCH_SIZE_KEY);
    }
  }

  /**
   * Deletes the field while a transaction of its own removes the value from the records that the
   * cleanup's first batch picks, and commits once that batch waits for it, so the batch updates none.
   */
  private static void deleteFieldWhileFirstBatchIsWrittenConcurrently(JsonObject field, String refId,
                                                                      int batchSize) {
    var writer = openConnection();
    Transaction transaction = null;
    try {
      transaction = await(writer.begin());
      await(writer.query("UPDATE item SET jsonb = jsonb_set(jsonb, '{customFields}', (jsonb->'customFields') - '"
        + refId + "') WHERE id IN (SELECT id FROM item WHERE jsonb->'customFields' ? '" + refId + "' LIMIT "
        + batchSize + ")").execute());
      var response = await(doDelete(client, CUSTOM_FIELDS_PATH + "/" + id(field)));
      assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
      awaitWaitingLock();
      await(transaction.commit());
      transaction = null;
    } finally {
      if (transaction != null) {
        await(transaction.rollback());
      }
      await(writer.close());
    }
  }

  /** Takes the lock that a cleanup pass holds on the tenant, as a pass on another instance would. */
  private static void holdCleanupLock(PgConnection connection) {
    await(connection.preparedQuery("SELECT pg_advisory_lock(hashtext('custom_fields_cleanup'), hashtext($1))")
      .execute(Tuple.of(PostgresClient.convertToPsqlStandard(TENANT_ID))));
  }

  private static void releaseCleanupLock(PgConnection connection) {
    await(connection.preparedQuery("SELECT pg_advisory_unlock(hashtext('custom_fields_cleanup'), hashtext($1))")
      .execute(Tuple.of(PostgresClient.convertToPsqlStandard(TENANT_ID))));
  }

  private static void awaitWaitingLock() {
    Awaitility.await()
      .atMost(Duration.ofSeconds(15))
      .pollInterval(Duration.ofMillis(100))
      .untilAsserted(() -> assertThat(runQuery("SELECT count(*) FROM pg_locks WHERE NOT granted")
        .iterator().next().getLong(0)).isPositive());
  }

  /** Makes every update of an item holding a value of the custom field fail, standing in for any failing removal. */
  private static void refuseItemUpdatesRemoving(String refId) {
    runQuery("CREATE FUNCTION fail_cleanup() RETURNS trigger AS $$ BEGIN RAISE EXCEPTION 'cleanup refused'; END $$"
      + " LANGUAGE plpgsql");
    runQuery("CREATE TRIGGER fail_cleanup BEFORE UPDATE ON item FOR EACH ROW"
      + " WHEN (OLD.jsonb->'customFields' ? '" + refId + "') EXECUTE FUNCTION fail_cleanup()");
  }

  private static void awaitCleanupDone() {
    awaitCleanupQueueSize(0);
  }

  private static void awaitCleanupQueueSize(long size) {
    Awaitility.await()
      .atMost(Duration.ofSeconds(15))
      .pollInterval(Duration.ofMillis(200))
      .untilAsserted(() -> assertThat(cleanupQueueSize()).isEqualTo(size));
  }

  private static long cleanupQueueSize() {
    return runQuery("SELECT count(*) FROM custom_fields_cleanup").iterator().next().getLong(0);
  }

  private static long placeholderCount() {
    return runQuery("SELECT count(*) FROM custom_fields WHERE jsonb->>'entityType' = '"
      + CustomFieldsValueCleanup.PLACEHOLDER_ENTITY_TYPE + "'").iterator().next().getLong(0);
  }

  private static String insertPlaceholder(String refId) {
    var id = UUID.randomUUID().toString();
    runQuery("INSERT INTO custom_fields (id, jsonb) VALUES ('" + id + "', jsonb_build_object('id', '" + id
      + "', 'name', '" + refId + "', 'refId', '" + refId + "', 'type', 'TEXTBOX_SHORT', 'entityType', '"
      + CustomFieldsValueCleanup.PLACEHOLDER_ENTITY_TYPE + "', 'visible', false, 'order', 1))");
    return id;
  }

  private static void insertFieldCleanupRow(String refId) {
    insertFieldCleanupRow(ENTITY_TYPE_ITEM, refId);
  }

  private static void insertFieldCleanupRow(String entityType, String refId) {
    runQuery("INSERT INTO custom_fields_cleanup (id, entity_type, ref_id) VALUES ('"
      + UUID.randomUUID() + "', '" + entityType + "', '" + refId + "')");
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
