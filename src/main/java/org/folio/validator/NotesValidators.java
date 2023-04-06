package org.folio.validator;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.tools.utils.ValidationHelper.createValidationErrorMessage;

import io.vertx.core.Future;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;
import org.folio.rest.exceptions.ValidationException;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.Note;

public final class NotesValidators {
  public static final int MAX_NOTE_LENGTH = 32000;

  private NotesValidators() {
  }

  private static boolean isTooLong(String note) {
    return StringUtils.length(note) > MAX_NOTE_LENGTH;
  }

  private static <T> Future<T> checkNotes(T entity, Function<T, List<String>> getAdministrativeNotes, Function<T, List<Note>> getNotes) {
    //both notes and administrativeNotes
    if (entity != null) {
      for (String administrativeNote : getAdministrativeNotes.apply(entity)) {
        if (isTooLong(administrativeNote)) {
          //should return note? min MAX_NOTE_LENGTH + 1 chars, will someone ned this?
          return failedFuture(new ValidationException(createValidationErrorMessage("administrativeNotes", administrativeNote, String.format(Locale.US, "A note has exceeded the %,d character limit.", MAX_NOTE_LENGTH))));
        }
      }

      for (Note note : getNotes.apply(entity)) {
        if (isTooLong(note.getNote())) {
          return failedFuture(new ValidationException(createValidationErrorMessage("notes", note.getNote(), String.format(Locale.US, "A note has exceeded the %,d character limit.", MAX_NOTE_LENGTH))));
        }
      }
    }

    return succeededFuture(entity);
  }

  private static <T> Future<List<T>> checkNotesList(List<T> list, Function<T, List<String>> getAdministrativeNotes, Function<T, List<Note>> getNotes) {
    for (T entity : list) {
      var result = checkNotes(entity, getAdministrativeNotes, getNotes);
      if (result.failed()) {
        return failedFuture(result.cause());
      }
    }
    return succeededFuture(list);
  }

  public static Future<HoldingsRecord> refuseLongNotes(HoldingsRecord holdingsRecord) {
    return checkNotes(holdingsRecord, HoldingsRecord::getAdministrativeNotes, HoldingsRecord::getNotes);
  }

  public static Future<Instance> refuseLongNotes(Instance instance) {
    return checkNotes(instance, Instance::getAdministrativeNotes, Instance::getNotes);
  }

  public static Future<Item> refuseLongNotes(Item item) {
    return checkNotes(item, Item::getAdministrativeNotes, Item::getNotes);
  }

  /**
   * For batch updates
   *
   * @param items
   * @return
   */
  public static Future<List<Item>> refuseItemLongNotes(List<Item> items) {
    return checkNotesList(items, Item::getAdministrativeNotes, Item::getNotes);
  }

  /**
   * For batch updates
   *
   * @param instances
   * @return
   */
  public static Future<List<Instance>> refuseInstanceLongNotes(List<Instance> instances) {
    return checkNotesList(instances, Instance::getAdministrativeNotes, Instance::getNotes);
  }

  /**
   * For batch updates
   *
   * @param holdingsRecords
   * @return
   */
  public static Future<List<HoldingsRecord>> refuseHoldingLongNotes(List<HoldingsRecord> holdingsRecords) {
    return checkNotesList(holdingsRecords, HoldingsRecord::getAdministrativeNotes, HoldingsRecord::getNotes);
  }
}
