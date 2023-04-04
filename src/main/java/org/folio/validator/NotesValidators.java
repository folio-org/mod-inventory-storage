package org.folio.validator;

import io.vertx.core.Future;
import org.apache.commons.lang3.StringUtils;
import org.folio.rest.exceptions.ValidationException;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.Note;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.tools.utils.ValidationHelper.createValidationErrorMessage;

public final class NotesValidators {
  public static final int MAX_NOTE_LENGTH = 32000;

  private NotesValidators() {
  }

  private static boolean isToLong(String note) {
    return StringUtils.length(note) > MAX_NOTE_LENGTH;
  }

  private static <T> void checkNotes(T entity, Function<T, List<String>> getAdministrativeNotes, Function<T, List<Note>> getNotes) {
    //both notes and administrativeNotes
    for (String administrativeNote : getAdministrativeNotes.apply(entity)) {
      if (isToLong(administrativeNote)) {
        //should return note? min MAX_NOTE_LENGTH + 1 chars, will someone ned this?
        throw new ValidationException(createValidationErrorMessage("administrativeNotes", administrativeNote,
          String.format(Locale.US, "A note has exceeded the %,d character limit.", MAX_NOTE_LENGTH)));
      }
    }

    for (Note note : getNotes.apply(entity)) {
      if (isToLong(note.getNote())) {
        //same here
        throw new ValidationException(createValidationErrorMessage("notes", note.getNote(),
          String.format(Locale.US, "A note has exceeded the %,d character limit.", MAX_NOTE_LENGTH)));
      }
    }
  }

  /**
   * For batch update
   *
   * @param holdingsRecords
   * @return
   */
  public static Future<List<HoldingsRecord>> refuseIfNoteMaxLengthExceed(List<HoldingsRecord> holdingsRecords) {
    try {
      for (HoldingsRecord holdingsRecord : holdingsRecords) {
        if (holdingsRecord != null) {
          checkNotes(holdingsRecord, HoldingsRecord::getAdministrativeNotes, HoldingsRecord::getNotes);
        }
      }
    } catch (ValidationException ex) {
      return failedFuture(ex);
    }

    return succeededFuture(holdingsRecords);
  }

  public static Future<HoldingsRecord> refuseIfNoteMaxLengthExceed(HoldingsRecord holdingsRecord) {
    try {
      if (holdingsRecord != null) {
        checkNotes(holdingsRecord, HoldingsRecord::getAdministrativeNotes, HoldingsRecord::getNotes);
      }
    } catch (ValidationException ex) {
      return failedFuture(ex);
    }

    return succeededFuture(holdingsRecord);
  }

  public static Future<Instance> refuseIfNoteMaxLengthExceed(Instance instance) {
    try {
      if (instance != null) {
        checkNotes(instance, Instance::getAdministrativeNotes, Instance::getNotes);
      }
    } catch (ValidationException ex) {
      return failedFuture(ex);
    }

    return succeededFuture(instance);
  }

  public static Future<List<Instance>> refuseIfInstanceNoteMaxLengthExceed(List<Instance> instances) {
    try {
      for (Instance instance : instances) {
        if (instance != null) {
          checkNotes(instance, Instance::getAdministrativeNotes, Instance::getNotes);
        }
      }
    } catch (ValidationException ex) {
      return failedFuture(ex);
    }

    return succeededFuture(instances);
  }

  public static Future<Item> refuseIfNoteMaxLengthExceed(Item item) {
    try {
      if (item != null) {
        checkNotes(item, Item::getAdministrativeNotes, Item::getNotes);
      }
    } catch (ValidationException ex) {
      return failedFuture(ex);
    }

    return succeededFuture(item);
  }

  public static Future<List<Item>> refuseIfItemNoteMaxLengthExceed(List<Item> items) {
    try {
      for (Item item : items) {
        if (item != null) {
          checkNotes(item, Item::getAdministrativeNotes, Item::getNotes);
        }
      }
    } catch (ValidationException ex) {
      return failedFuture(ex);
    }

    return succeededFuture(items);
  }
}
