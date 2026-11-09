package org.folio.it.api.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.HttpStatus.SC_CONFLICT;
import static org.folio.HttpStatus.SC_CREATED;
import static org.folio.HttpStatus.SC_NOT_FOUND;
import static org.folio.HttpStatus.SC_NO_CONTENT;
import static org.folio.HttpStatus.SC_OK;
import static org.folio.HttpStatus.SC_UNPROCESSABLE_CONTENT;
import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;
import static org.folio.it.HoldingsStorageFixtures.createHolding;
import static org.folio.it.InstanceStorageFixtures.createInstance;
import static org.folio.rest.jaxrs.model.CustomField.Type.MULTI_SELECT_DROPDOWN;
import static org.folio.rest.jaxrs.model.CustomField.Type.RADIO_BUTTON;
import static org.folio.rest.jaxrs.model.CustomField.Type.SINGLE_SELECT_DROPDOWN;
import static org.folio.rest.jaxrs.model.CustomField.Type.TEXTBOX_SHORT;
import static org.folio.support.ResourcePaths.ITEMS;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgConnection;
import io.vertx.sqlclient.Transaction;
import io.vertx.sqlclient.Tuple;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.awaitility.Awaitility;
import org.folio.persist.CustomFieldsCleanupRepository;
import org.folio.rest.jaxrs.model.CustomField;
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
 * gone, the values are removed in the background, a field created while a deleted field's values
 * remain gets a new refId, and removing an option is refused while items hold it.
 */
class ItemStorageCustomFieldsIT extends ItemStorageTestBase {

  private static final String CUSTOM_FIELDS = "customFields";
  private static final String ENTITY_TYPE = "entityType";
  private static final String REF_ID = "refId";
  private static final String SELECT_FIELD = "selectField";
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
    textboxField = customField(TEXTBOX, TEXTBOX_SHORT, null);
    singleSelectField = customField(SINGLE_SELECT, SINGLE_SELECT_DROPDOWN, selectField(3, false));
    multiSelectField = customField(MULTI_SELECT, MULTI_SELECT_DROPDOWN, selectField(4, true));
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
    var userField = customField(TEXTBOX, TEXTBOX_SHORT, null).put(ENTITY_TYPE, "user");

    var postResponse = await(doPost(client, CUSTOM_FIELDS_PATH, userField));
    var putResponse = await(doPut(client, CUSTOM_FIELDS_PATH, new JsonObject()
      .put(CUSTOM_FIELDS, new JsonArray().add(userField))
      .put(ENTITY_TYPE, "user")));

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
    assertThat(storedCustomFieldsOf(item)).isEqualTo(new JsonObject().put(SINGLE_SELECT, "opt_0"));
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

    var singleReduced = singleSelectField.copy().put(SELECT_FIELD, selectField(new JsonArray()
      .add(option("opt_1", "opt1"))
      .add(option("opt_2", "opt2")), false));
    var multiReduced = multiSelectField.copy().put(SELECT_FIELD, selectField(2, true));
    var fieldResponse = await(doPut(client, CUSTOM_FIELDS_PATH + "/" + id(singleSelectField), singleReduced));
    var collectionResponse = await(doPut(client, CUSTOM_FIELDS_PATH, collection(singleSelectField, multiReduced)));

    assertThat(fieldResponse.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
    assertThat(fieldResponse.body().toString())
      .contains("Option 'opt0' of custom field 'singleselect' is used by 1 item(s) and cannot be removed");
    assertThat(collectionResponse.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
    assertThat(collectionResponse.body().toString())
      .contains("Option 'opt2' of custom field 'multiselect' is used by 1 item(s) and cannot be removed");
    assertThat(customFieldNames()).containsExactlyInAnyOrder(TEXTBOX, SINGLE_SELECT, MULTI_SELECT);
    assertThat(storedOptionIdsOf(singleSelectField)).containsExactly("opt_0", "opt_1", "opt_2");
    assertThat(storedOptionIdsOf(multiSelectField)).containsExactly("opt_0", "opt_1", "opt_2", "opt_3");
    assertThat(cleanupQueueSize()).isZero();
    assertThat(storedCustomFieldsOf(item)).isEqualTo(allValues());
  }

  @Test
  @DisplayName("should remove an option that no item holds")
  void shouldRemoveOption_whenNoItemHoldsIt() {
    createCustomField(singleSelectField);
    final var item = createItem(itemWithCustomFields(new JsonObject().put(SINGLE_SELECT, "opt_0")));

    singleSelectField.put(SELECT_FIELD, selectField(2, false));
    var response = await(doPut(client, CUSTOM_FIELDS_PATH + "/" + id(singleSelectField), singleSelectField));

    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
    assertThat(storedOptionIdsOf(singleSelectField)).containsExactly("opt_0", "opt_1");
    assertThat(cleanupQueueSize()).isZero();
    assertThat(storedCustomFieldsOf(item)).isEqualTo(new JsonObject().put(SINGLE_SELECT, "opt_0"));
  }

  @Test
  @DisplayName("should delete a field in the background and remove an unused option in one collection replacement")
  void shouldReplaceCollection_deletingFieldAndRemovingUnusedOption() {
    createCustomField(textboxField);
    createCustomField(singleSelectField);
    createCustomField(multiSelectField);
    final var item = createItem(itemWithCustomFields(allValues()));

    var reduced = singleSelectField.copy().put(SELECT_FIELD, selectField(2, false));
    var response = await(doPut(client, CUSTOM_FIELDS_PATH, collection(reduced, multiSelectField)));

    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
    assertThat(customFieldNames()).containsExactlyInAnyOrder(SINGLE_SELECT, MULTI_SELECT);
    assertThat(storedOptionIdsOf(singleSelectField)).containsExactly("opt_0", "opt_1");
    awaitCleanupDone();
    assertThat(storedCustomFieldsOf(item)).isEqualTo(new JsonObject()
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
      assertThat(storedCustomFieldsOf(item)).isEqualTo(new JsonObject());
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
        assertThat(storedCustomFieldsOf(itemId)).isEqualTo(new JsonObject());
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
    var updated = await(doPut(client, CUSTOM_FIELDS_PATH + "/" + placeholderId, textboxField));
    var statistic = await(doGet(client, fieldStatisticPath(placeholderId)));
    var optionStatistic = await(doGet(client, optionStatisticPath(placeholderId, "opt_0")));
    var deleted = await(doDelete(client, CUSTOM_FIELDS_PATH + "/" + placeholderId));
    var replaced = await(doPut(client, CUSTOM_FIELDS_PATH, new JsonObject()
      .put(CUSTOM_FIELDS, new JsonArray())
      .put(ENTITY_TYPE, CustomFieldsCleanupRepository.PLACEHOLDER_ENTITY_TYPE)));
    var created = createCustomField(textboxField);

    assertThat(byQuery.jsonBody().getJsonArray(CUSTOM_FIELDS)).isEmpty();
    assertThat(byId.status()).isEqualTo(SC_NOT_FOUND);
    assertThat(updated.status()).isEqualTo(SC_NOT_FOUND);
    assertThat(statistic.status()).isEqualTo(SC_NOT_FOUND);
    assertThat(optionStatistic.status()).isEqualTo(SC_NOT_FOUND);
    assertThat(deleted.status()).isEqualTo(SC_NOT_FOUND);
    assertThat(replaced.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
    assertThat(created.getString(REF_ID)).isEqualTo("textbox_2");
    assertThat(customFieldNames()).containsExactly(TEXTBOX);
    assertThat(placeholderCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("should give a field deleted and recreated in one collection replacement the next refId")
  void shouldRecreateField_withNextRefId_inOneCollectionReplacement() {
    createCustomField(textboxField);
    final var item = createItem(itemWithCustomFields(new JsonObject().put(TEXTBOX, "text1")));

    var sameNameNewId = customField(TEXTBOX, TEXTBOX_SHORT, null);
    var response = await(doPut(client, CUSTOM_FIELDS_PATH, collection(sameNameNewId)));

    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
    assertThat(getCustomField(sameNameNewId).getString(REF_ID)).isEqualTo("textbox_2");
    awaitCleanupDone();
    assertThat(storedCustomFieldsOf(item)).isEqualTo(new JsonObject());
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
    singleSelectField.put(SELECT_FIELD, selectField(options, false));
    var response = await(doPut(client, CUSTOM_FIELDS_PATH + "/" + id(singleSelectField), singleSelectField));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
    assertThat(storedOptionIdsOf(singleSelectField)).containsExactly("opt_0", "opt_1", "opt_2");
    assertThat(storedCustomFieldsOf(item)).isEqualTo(new JsonObject().put(SINGLE_SELECT, "opt_2"));
  }

  @Test
  @DisplayName("should queue no value removal when a collection replacement fails after deleting a field")
  void shouldQueueNothing_whenCollectionReplacementFails() {
    createCustomField(textboxField);
    createCustomField(singleSelectField);
    final var item = createItem(itemWithCustomFields(new JsonObject().put(TEXTBOX, "text1")));

    var typeChanged = singleSelectField.copy().put("type", RADIO_BUTTON.value());
    var response = await(doPut(client, CUSTOM_FIELDS_PATH, collection(typeChanged)));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
    assertThat(customFieldNames()).containsExactlyInAnyOrder(TEXTBOX, SINGLE_SELECT);
    assertThat(cleanupQueueSize()).isZero();
    assertThat(storedCustomFieldsOf(item).getString(TEXTBOX)).isEqualTo("text1");
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
    assertThat(storedCustomFieldsOf(item)).isEqualTo(new JsonObject());
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
    assertThat(createCustomField(textboxField).getString(REF_ID)).isEqualTo(TEXTBOX);
  }

  @Test
  @DisplayName("should keep a failing removal with its error for the next pass and process the rows after it")
  void shouldKeepFailingRemoval_andProcessRowsAfterIt() {
    var refusedRefId = "refused";
    refuseItemUpdatesRemoving(refusedRefId);
    try {
      final var refusedItem = createItem(itemWithCustomFields(new JsonObject().put(refusedRefId, "x")));
      final var item = createItem(itemWithCustomFields(new JsonObject().put(TEXTBOX, "text1")));
      insertFieldCleanupRow(refusedRefId);
      insertFieldCleanupRow(TEXTBOX);

      var response = await(doPost(client, CLEANUP_PATH, null));

      assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
      awaitCleanupQueueSize(1);
      var kept = runQuery("SELECT ref_id, attempts, last_error, last_attempt_date FROM custom_fields_cleanup")
        .iterator().next();
      assertThat(kept.getString("ref_id")).isEqualTo(refusedRefId);
      assertThat(kept.getInteger("attempts")).isEqualTo(1);
      assertThat(kept.getString("last_error")).contains("cleanup refused");
      assertThat(kept.getValue("last_attempt_date")).isNotNull();
      assertThat(storedCustomFieldsOf(item)).isEqualTo(new JsonObject());
      assertThat(storedCustomFieldsOf(refusedItem).getString(refusedRefId)).isEqualTo("x");
    } finally {
      runQuery("DROP TRIGGER IF EXISTS fail_cleanup ON item");
      runQuery("DROP FUNCTION IF EXISTS fail_cleanup()");
    }
  }

  @Test
  @DisplayName("should remove the values of the items after one that cannot be updated and retry it in the next pass")
  void shouldRemoveValuesAfterUnwritableItem_andRetryItInNextPass() {
    createCustomField(textboxField);
    final var first = createItem(itemWithCustomFields(new JsonObject().put(TEXTBOX, "text1")));
    final var unwritable = createItem(itemWithCustomFields(new JsonObject().put(TEXTBOX, "text2")));
    final var last = createItem(itemWithCustomFields(new JsonObject().put(TEXTBOX, "text3")));
    refuseUpdatesOfItem(id(unwritable));
    try {
      deleteField(textboxField);

      awaitCleanupAttempts(1);
      assertThat(runQuery("SELECT last_error FROM custom_fields_cleanup").iterator().next().getString(0))
        .contains("1 of the records", id(unwritable), "cleanup refused");
      assertThat(storedCustomFieldsOf(first)).isEqualTo(new JsonObject());
      assertThat(storedCustomFieldsOf(last)).isEqualTo(new JsonObject());
      assertThat(storedCustomFieldsOf(unwritable).getString(TEXTBOX)).isEqualTo("text2");
      assertThat(placeholderCount()).isEqualTo(1);

      runQuery("DROP TRIGGER fail_cleanup ON item");
      await(doPost(client, CLEANUP_PATH, null));

      awaitCleanupDone();
      assertThat(storedCustomFieldsOf(unwritable)).isEqualTo(new JsonObject());
      assertThat(placeholderCount()).isZero();
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
      assertThat(storedCustomFieldsOf(item).getString(TEXTBOX)).isEqualTo("text1");
    } finally {
      releaseCleanupLock(otherInstance);
      await(otherInstance.close());
    }

    await(doPost(client, CLEANUP_PATH, null));

    awaitCleanupDone();
    assertThat(storedCustomFieldsOf(item)).isEqualTo(new JsonObject());
  }

  @Test
  @DisplayName("should remove the values of a field queued while a pass runs in the same pass")
  void shouldRemoveValuesOfFieldQueuedDuringPass_inSamePass() {
    createCustomField(textboxField);
    final var paused = createItem(itemWithCustomFields(new JsonObject().put(TEXTBOX, "text1")));
    final var queuedLater = createItem(itemWithCustomFields(new JsonObject().put(SINGLE_SELECT, "opt_0")));

    inTransactionOfItsOwn(connection -> {
      execute(connection, "SELECT 1 FROM item WHERE id = $1 FOR UPDATE", UUID.fromString(id(paused)));
      deleteField(textboxField);
      awaitWaitingLocks(1);
      insertFieldCleanupRow(SINGLE_SELECT);
      return null;
    });

    awaitCleanupDone();
    assertThat(storedCustomFieldsOf(paused)).isEqualTo(new JsonObject());
    assertThat(storedCustomFieldsOf(queuedLater)).isEqualTo(new JsonObject());
  }

  @Test
  @DisplayName("should go on removing values when a concurrent write leaves a batch with no record to update")
  void shouldContinueRemoval_whenConcurrentWriteEmptiesBatch() {
    createCustomField(textboxField);
    var items = new ArrayList<String>();
    for (int i = 0; i < 5; i++) {
      items.add(createItem(itemWithCustomFields(new JsonObject().put(TEXTBOX, "text" + i))).getString("id"));
    }
    var target = createItem(itemWithCustomFields(new JsonObject())).getString("id");
    items.add(target);

    deleteFieldWhileConcurrentWriteMovesValue(textboxField, TEXTBOX, target);

    awaitCleanupDone();
    for (String itemId : items) {
      assertThat(storedCustomFieldsOf(itemId)).isEqualTo(new JsonObject());
    }
    assertThat(placeholderCount()).isZero();
  }

  @Test
  @DisplayName("should not deadlock with a write of an item that a batch has not locked yet, "
    + "in a holdings record whose other items the batch holds")
  void shouldNotDeadlock_withWriteOfItemNotYetLockedByBatch() {
    createCustomField(textboxField);
    final var first = createItem(itemWithCustomFields(new JsonObject().put(TEXTBOX, "text1")));
    final var paused = createItem(itemWithCustomFields(new JsonObject().put(TEXTBOX, "text2")));
    final var written = createItem(itemWithCustomFields(new JsonObject().put(TEXTBOX, "text3")));

    inTransactionOfItsOwn(connection -> {
      execute(connection, "SELECT 1 FROM item WHERE id = $1 FOR UPDATE", UUID.fromString(id(paused)));
      deleteField(textboxField);
      awaitWaitingLocks(1);
      var write = doPut(client, ITEMS + "/" + id(written),
        getItemJsonById(id(written)).put("itemLevelCallNumber", "written"));
      Awaitility.await().atMost(Duration.ofSeconds(10)).until(write::isComplete);
      assertThat(await(write).status()).isEqualTo(SC_NO_CONTENT);
      return null;
    });

    awaitCleanupDone();
    for (JsonObject item : List.of(first, paused, written)) {
      assertThat(storedCustomFieldsOf(item)).isEqualTo(new JsonObject());
    }
  }

  @Test
  @DisplayName("should not deadlock with a write of an item outside a batch, "
    + "in a holdings record of an instance the batch holds")
  void shouldNotDeadlock_withWriteOfItemOutsideBatchOnBatchInstance() {
    createCustomField(textboxField);
    var instanceId = createInstance(client, "an instance " + UUID.randomUUID(), instanceTypeId);
    var firstHoldingId = createHolding(client, instanceId, mainLibraryLocationId);
    var secondHoldingId = createHolding(client, instanceId, mainLibraryLocationId);
    final var first = createItem(minimalItemRequest(UUID.randomUUID(), firstHoldingId)
      .put(CUSTOM_FIELDS, new JsonObject().put(TEXTBOX, "text1")));
    final var second = createItem(minimalItemRequest(UUID.randomUUID(), secondHoldingId)
      .put(CUSTOM_FIELDS, new JsonObject().put(TEXTBOX, "text2")));
    final var written = createItem(minimalItemRequest(UUID.randomUUID(), secondHoldingId));

    var write = inTransactionOfItsOwn(connection -> {
      execute(connection, "SELECT 1 FROM item_order_tracker WHERE holdings_id = $1 FOR UPDATE",
        UUID.fromString(secondHoldingId));
      final var waitingWrite = doPut(client, ITEMS + "/" + id(written),
        getItemJsonById(id(written)).put("itemLevelCallNumber", "written"));
      awaitWaitingLocks(1);
      deleteField(textboxField);
      awaitWaitingLocks(2);
      return waitingWrite;
    });

    assertThat(await(write).status()).isEqualTo(SC_NO_CONTENT);
    awaitCleanupDone();
    assertThat(storedCustomFieldsOf(first)).isEqualTo(new JsonObject());
    assertThat(storedCustomFieldsOf(second)).isEqualTo(new JsonObject());
  }

  /**
   * Removes the value from every record holding it and stores it on the target record in a transaction on a
   * connection of its own, deletes the field, and commits once the cleanup waits for one of those records.
   * The cleanup then finds the value gone from every record of its first batch, which misses the target.
   */
  private static void deleteFieldWhileConcurrentWriteMovesValue(JsonObject field, String refId, String targetId) {
    inTransactionOfItsOwn(connection -> {
      execute(connection, "UPDATE item SET jsonb = jsonb_set(jsonb, '{customFields}', (jsonb->'customFields') - $1)"
        + " WHERE jsonb->'customFields' ? $1", refId);
      execute(connection, "UPDATE item SET jsonb = jsonb_set(jsonb, '{customFields}',"
        + " COALESCE(jsonb->'customFields', '{}') || jsonb_build_object($1::text, 'moved')) WHERE id = $2",
        refId, UUID.fromString(targetId));
      deleteField(field);
      awaitWaitingLocks(1);
      return null;
    });
  }

  /**
   * Runs the steps in a transaction on a connection of its own and commits it. If a step fails, the
   * transaction is rolled back, since a pooled connection returned with an open transaction keeps its locks.
   */
  private static <T> T inTransactionOfItsOwn(Function<PgConnection, T> steps) {
    var connection = openConnection();
    Transaction transaction = null;
    try {
      transaction = await(connection.begin());
      var result = steps.apply(connection);
      await(transaction.commit());
      transaction = null;
      return result;
    } finally {
      if (transaction != null) {
        await(transaction.rollback());
      }
      await(connection.close());
    }
  }

  private static void execute(PgConnection connection, String sql, Object... params) {
    await(connection.preparedQuery(sql).execute(Tuple.wrap(params)));
  }

  private static void deleteField(JsonObject field) {
    var response = await(doDelete(client, CUSTOM_FIELDS_PATH + "/" + id(field)));
    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
  }

  /** Takes the lock that a cleanup pass holds on the tenant, as a pass on another instance would. */
  private static void holdCleanupLock(PgConnection connection) {
    await(connection.preparedQuery("SELECT pg_advisory_lock(" + CustomFieldsCleanupRepository.LOCK_KEY + ")")
      .execute(Tuple.of(PostgresClient.convertToPsqlStandard(TENANT_ID))));
  }

  private static void releaseCleanupLock(PgConnection connection) {
    await(connection.preparedQuery("SELECT pg_advisory_unlock(" + CustomFieldsCleanupRepository.LOCK_KEY + ")")
      .execute(Tuple.of(PostgresClient.convertToPsqlStandard(TENANT_ID))));
  }

  private static void awaitWaitingLocks(long count) {
    Awaitility.await()
      .atMost(Duration.ofSeconds(15))
      .pollInterval(Duration.ofMillis(100))
      .untilAsserted(() -> assertThat(runQuery("SELECT count(*) FROM pg_locks WHERE NOT granted")
        .iterator().next().getLong(0)).isGreaterThanOrEqualTo(count));
  }

  /** Makes every update of an item holding a value of the custom field fail, standing in for any failing removal. */
  private static void refuseItemUpdatesRemoving(String refId) {
    refuseItemUpdatesWhere("OLD.jsonb->'customFields' ? '" + refId + "'");
  }

  /** Makes every update of the item fail, as for an item whose instance no longer passes its own checks. */
  private static void refuseUpdatesOfItem(String itemId) {
    refuseItemUpdatesWhere("OLD.id = '" + itemId + "'");
  }

  private static void refuseItemUpdatesWhere(String condition) {
    runQuery("CREATE FUNCTION fail_cleanup() RETURNS trigger AS $$ BEGIN RAISE EXCEPTION 'cleanup refused'; END $$"
      + " LANGUAGE plpgsql");
    runQuery("CREATE TRIGGER fail_cleanup BEFORE UPDATE ON item FOR EACH ROW WHEN (" + condition + ")"
      + " EXECUTE FUNCTION fail_cleanup()");
  }

  /** Waits until the queue row has the attempts and keeps them for a second, as a pass that retries it would not. */
  private static void awaitCleanupAttempts(int attempts) {
    Awaitility.await()
      .during(Duration.ofSeconds(1))
      .atMost(Duration.ofSeconds(15))
      .pollInterval(Duration.ofMillis(200))
      .untilAsserted(() -> assertThat(runReadQuery("SELECT attempts FROM custom_fields_cleanup")
        .iterator().next().getInteger(0)).isEqualTo(attempts));
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

  /**
   * Counts the queue rows on the standby that serves the module's reads. The cleanup commits asynchronously,
   * so the standby applies its removals after the primary shows them, and in commit order: once the standby
   * shows a queue row gone, it shows that row's removals too.
   */
  private static long cleanupQueueSize() {
    return runReadQuery("SELECT count(*) FROM custom_fields_cleanup").iterator().next().getLong(0);
  }

  private static long placeholderCount() {
    return runQuery("SELECT count(*) FROM custom_fields WHERE jsonb->>'entityType' = '"
      + CustomFieldsCleanupRepository.PLACEHOLDER_ENTITY_TYPE + "'").iterator().next().getLong(0);
  }

  private static String insertPlaceholder(String refId) {
    var id = UUID.randomUUID().toString();
    runQuery("INSERT INTO custom_fields (id, jsonb) VALUES ('" + id + "', jsonb_build_object('id', '" + id
      + "', 'name', '" + refId + "', 'refId', '" + refId + "', 'type', '" + TEXTBOX_SHORT.value() + "', 'entityType', '"
      + CustomFieldsCleanupRepository.PLACEHOLDER_ENTITY_TYPE + "', 'visible', false, 'order', 1))");
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

  private static JsonObject customField(String name, CustomField.Type type, JsonObject selectField) {
    var field = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("name", name)
      .put("type", type.value())
      .put(ENTITY_TYPE, ENTITY_TYPE_ITEM);
    if (selectField != null) {
      field.put(SELECT_FIELD, selectField);
    }
    return field;
  }

  private static JsonObject selectField(int numberOfOptions, boolean multiSelect) {
    var options = new JsonArray();
    for (int i = 0; i < numberOfOptions; i++) {
      options.add(option("opt_" + i, "opt" + i));
    }
    return selectField(options, multiSelect);
  }

  private static JsonObject selectField(JsonArray options, boolean multiSelect) {
    return new JsonObject()
      .put("multiSelect", multiSelect)
      .put("options", new JsonObject().put("values", options));
  }

  private static JsonObject option(String id, String value) {
    return new JsonObject().put("id", id).put("value", value);
  }

  private static JsonObject collection(JsonObject... fields) {
    return new JsonObject()
      .put(CUSTOM_FIELDS, new JsonArray(List.of((Object[]) fields)))
      .put(ENTITY_TYPE, ENTITY_TYPE_ITEM);
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

  private static String id(JsonObject json) {
    return json.getString("id");
  }

  private static JsonObject customFieldsOf(JsonObject item) {
    return item.getJsonObject(CUSTOM_FIELDS, new JsonObject());
  }

  private static JsonObject storedCustomFieldsOf(JsonObject item) {
    return storedCustomFieldsOf(id(item));
  }

  private static JsonObject storedCustomFieldsOf(String itemId) {
    return customFieldsOf(getItemJsonById(itemId));
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

  private static List<String> storedOptionIdsOf(JsonObject field) {
    return getCustomField(field).getJsonObject(SELECT_FIELD).getJsonObject("options").getJsonArray("values")
      .stream()
      .map(option -> ((JsonObject) option).getString("id"))
      .toList();
  }

  private static int fieldStatisticCount(JsonObject field) {
    return statisticCount(fieldStatisticPath(id(field)));
  }

  private static int optionStatisticCount(JsonObject field, String optionId) {
    return statisticCount(optionStatisticPath(id(field), optionId));
  }

  private static String fieldStatisticPath(String fieldId) {
    return CUSTOM_FIELDS_PATH + "/" + fieldId + "/stats";
  }

  private static String optionStatisticPath(String fieldId, String optionId) {
    return CUSTOM_FIELDS_PATH + "/" + fieldId + "/options/" + optionId + "/stats";
  }

  private static int statisticCount(String path) {
    var response = await(doGet(client, path));
    assertThat(response.status()).isEqualTo(SC_OK);
    return response.jsonBody().getInteger("count");
  }
}
