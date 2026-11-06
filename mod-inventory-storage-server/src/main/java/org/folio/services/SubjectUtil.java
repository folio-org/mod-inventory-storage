package org.folio.services;

import static org.folio.rest.tools.utils.ValidationHelper.createValidationErrorMessage;

import io.vertx.core.Future;
import java.util.Map;
import java.util.Optional;
import org.folio.rest.exceptions.ValidationException;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.SubjectSource;
import org.folio.services.consortium.ConsortiumService;

public final class SubjectUtil {

  public static final String SOURCE_CANNOT_BE_FOLIO =
    "Illegal operation: Source field cannot be set to folio";
  public static final String SOURCE_FOLIO_CANNOT_BE_UPDATED =
    "Illegal operation: Source folio cannot be updated";
  public static final String SOURCE_CANNOT_BE_UPDATED_AT_NON_ECS =
    "Illegal operation: Source field cannot be updated at non-consortium tenant";
  public static final String SOURCE_CONSORTIUM_CANNOT_BE_APPLIED =
    "Illegal operation: Source consortium cannot be applied at non-consortium tenant";
  private static final String SOURCE = "source";

  private SubjectUtil() {
  }

  public static Future<Optional<Errors>> validateSubjectSourceCreate(String subjectSource,
                                                                     ConsortiumService consortiumService,
                                                                     Map<String, String> headers) {
    if (SubjectSource.Source.FOLIO.value().equals(subjectSource)) {
      return Future.succeededFuture(getValidationErrorMessage(subjectSource, SOURCE_CANNOT_BE_FOLIO));
    }

    if (SubjectSource.Source.CONSORTIUM.value().equals(subjectSource)) {
      return consortiumService.getConsortiumData(headers)
        .map(consortiumDataOptional -> {
          if (consortiumDataOptional.isEmpty()) {
            return getValidationErrorMessage(subjectSource, SOURCE_CONSORTIUM_CANNOT_BE_APPLIED);
          }
          return Optional.empty();
        });
    }
    return Future.succeededFuture(Optional.empty());
  }

  public static Future<Optional<Errors>> validateSubjectSourceUpdate(String incomingSubjectSource,
                                                                     String existingSubjectSource,
                                                                     ConsortiumService consortiumService,
                                                                     Map<String, String> headers) {
    if (!existingSubjectSource.equals(incomingSubjectSource)) {
      if (SubjectSource.Source.FOLIO.value().equals(incomingSubjectSource)) {
        return Future.succeededFuture(getValidationErrorMessage(incomingSubjectSource, SOURCE_CANNOT_BE_FOLIO));
      }
      if (SubjectSource.Source.FOLIO.value().equals(existingSubjectSource)) {
        return Future.succeededFuture(getValidationErrorMessage(incomingSubjectSource, SOURCE_FOLIO_CANNOT_BE_UPDATED));
      }
      return consortiumService.getConsortiumData(headers)
        .map(consortiumDataOptional -> {
          if (consortiumDataOptional.isEmpty()) {
            return getValidationErrorMessage(incomingSubjectSource, SOURCE_CANNOT_BE_UPDATED_AT_NON_ECS);
          }
          return Optional.empty();
        });
    }
    return Future.succeededFuture(Optional.empty());
  }

  public static <T> Future<T> sourceValidationError(Errors errors) {
    return Future.failedFuture(new ValidationException(errors));
  }

  private static Optional<Errors> getValidationErrorMessage(String subjectSource, String errorMessage) {
    return Optional.of(createValidationErrorMessage(SOURCE, subjectSource, errorMessage));
  }
}
