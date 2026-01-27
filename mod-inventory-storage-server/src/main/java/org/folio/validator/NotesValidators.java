package org.folio.validator;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.util.Objects.isNull;
import static org.folio.rest.tools.utils.ValidationHelper.createValidationErrorMessage;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;
import org.folio.rest.exceptions.ValidationException;
import org.folio.rest.jaxrs.model.HoldingsNote;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.InstanceNote;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.ItemNote;

public final class NotesValidators {
  public static final int MAX_NOTE_LENGTH = 32000;

  private NotesValidators() {
  }

  public static Future<HoldingsRecord> refuseLongNotes(HoldingsRecord holdingsRecord) {
    return checkNotes(holdingsRecord, HoldingsRecord::getAdministrativeNotes, holdingsRecordNotesFunction());
  }

  public static Future<Instance> refuseLongNotes(Instance instance) {
    return checkNotes(instance, Instance::getAdministrativeNotes, instanceNotesFunction());
  }

  public static Future<JsonObject> refuseLongNotes(JsonObject json) {
    return checkNotes(json, instancePatchAdministrativeNotesFunction(), instancePatchNotesFunction());
  }

  public static Future<Item> refuseLongNotes(Item item) {
    return checkNotes(item, Item::getAdministrativeNotes, itemNotesFunction());
  }

  /**
   * For batch updates.
   */
  public static Future<List<Item>> refuseItemLongNotes(List<Item> items) {
    return checkNotesList(items, Item::getAdministrativeNotes, itemNotesFunction());
  }

  /**
   * For batch updates.
   */
  public static Future<List<Instance>> refuseInstanceLongNotes(List<Instance> instances) {
    return checkNotesList(instances, Instance::getAdministrativeNotes, instanceNotesFunction());
  }

  /**
   * For batch updates.
   */
  public static Future<List<HoldingsRecord>> refuseHoldingLongNotes(List<HoldingsRecord> holdingsRecords) {
    return checkNotesList(holdingsRecords, HoldingsRecord::getAdministrativeNotes, holdingsRecordNotesFunction());
  }

  private static boolean isTooLong(String note) {
    return StringUtils.length(note) > MAX_NOTE_LENGTH;
  }

  private static Function<HoldingsRecord, List<String>> holdingsRecordNotesFunction() {
    return holdings -> holdings.getNotes().stream().map(
      HoldingsNote::getNote).toList();
  }

  private static Function<Instance, List<String>> instanceNotesFunction() {
    return instance -> instance.getNotes().stream().map(InstanceNote::getNote).toList();
  }

  private static Function<JsonObject, List<String>> instancePatchAdministrativeNotesFunction() {
    return json -> isNull(json.getJsonArray("administrativeNotes"))
      ? Collections.emptyList()
      : json.getJsonArray("administrativeNotes").stream().map(Object::toString).toList();
  }

  private static Function<JsonObject, List<String>> instancePatchNotesFunction() {
    return patch -> isNull(patch.getJsonArray("notes"))
      ? Collections.emptyList()
      : patch.getJsonArray("notes").stream()
        .map(obj -> (InstanceNote) obj)
        .map(InstanceNote::getNote).toList();
  }

  private static Function<Item, List<String>> itemNotesFunction() {
    return item -> item.getNotes().stream().map(ItemNote::getNote).toList();
  }

  private static <T> Future<T> checkNotes(T entity, Function<T, List<String>> getAdministrativeNotes,
                                          Function<T, List<String>> getNotes) {
    //both notes and administrativeNotes
    if (entity != null) {
      for (String administrativeNote : getAdministrativeNotes.apply(entity)) {
        if (isTooLong(administrativeNote)) {
          //should return note? min MAX_NOTE_LENGTH + 1 chars, will someone ned this?
          return failedFuture(new ValidationException(
            createValidationErrorMessage("administrativeNotes", administrativeNote,
              String.format(Locale.US, "A note has exceeded the %,d character limit.", MAX_NOTE_LENGTH))));
        }
      }

      for (String note : getNotes.apply(entity)) {
        if (isTooLong(note)) {
          return failedFuture(new ValidationException(createValidationErrorMessage("notes", note,
            String.format(Locale.US, "A note has exceeded the %,d character limit.", MAX_NOTE_LENGTH))));
        }
      }
    }

    return succeededFuture(entity);
  }

  private static <T> Future<List<T>> checkNotesList(List<T> list, Function<T, List<String>> getAdministrativeNotes,
                                                    Function<T, List<String>> getNotes) {
    for (T entity : list) {
      var result = checkNotes(entity, getAdministrativeNotes, getNotes);
      if (result.failed()) {
        return failedFuture(result.cause());
      }
    }
    return succeededFuture(list);
  }
}
