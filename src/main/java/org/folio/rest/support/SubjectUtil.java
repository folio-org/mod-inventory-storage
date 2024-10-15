package org.folio.rest.support;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.jaxrs.resource.SubjectSources.PostSubjectSourcesResponse.respond422WithApplicationJson;
import static org.folio.rest.support.ResponseUtil.SOURCE_CANNOT_BE_FOLIO;
import static org.folio.rest.support.ResponseUtil.SOURCE_CANNOT_BE_UPDATED_AT_NON_ECS;
import static org.folio.rest.support.ResponseUtil.SOURCE_CONSORTIUM_CANNOT_BE_APPLIED;
import static org.folio.rest.support.ResponseUtil.SOURCE_FOLIO_CANNOT_BE_UPDATED;
import static org.folio.rest.tools.utils.ValidationHelper.createValidationErrorMessage;

import io.vertx.core.Future;
import java.util.Map;
import java.util.Optional;
import javax.ws.rs.core.Response;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.SubjectSource;
import org.folio.services.consortium.ConsortiumService;

public final class SubjectUtil {
  private static final String SOURCE = "source";

  public static Future<Optional<Errors>> validateSubjectSourceCreate(String subjectSource,
                                                                     ConsortiumService consortiumService,
                                                                     Map<String, String> okapiHeaders) {
    if (SubjectSource.Source.FOLIO.value().equals(subjectSource)) {
      return Future.succeededFuture(getValidationErrorMessage(subjectSource, SOURCE_CANNOT_BE_FOLIO));
    }

    if (SubjectSource.Source.CONSORTIUM.value().equals(subjectSource)) {
      return consortiumService.getConsortiumData(okapiHeaders)
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
                                                                     Map<String, String> okapiHeaders) {
    if (!existingSubjectSource.equals(incomingSubjectSource)) {
      if (SubjectSource.Source.FOLIO.value().equals(incomingSubjectSource)) {
        return Future.succeededFuture(getValidationErrorMessage(incomingSubjectSource, SOURCE_CANNOT_BE_FOLIO));
      }
      if (SubjectSource.Source.FOLIO.value().equals(existingSubjectSource)) {
        return Future.succeededFuture(getValidationErrorMessage(incomingSubjectSource, SOURCE_FOLIO_CANNOT_BE_UPDATED));
      }
      return consortiumService.getConsortiumData(okapiHeaders)
        .map(consortiumDataOptional -> {
          if (consortiumDataOptional.isEmpty()) {
            return getValidationErrorMessage(incomingSubjectSource, SOURCE_CANNOT_BE_UPDATED_AT_NON_ECS);
          }
          return Optional.empty();
        });
    }
    return Future.succeededFuture(Optional.empty());
  }

  public static Future<Response> sourceValidationError(Errors errors) {
    return succeededFuture(respond422WithApplicationJson(errors));
  }

  private static Optional<Errors> getValidationErrorMessage(String subjectSource, String errorMessage) {
    return Optional.of(createValidationErrorMessage(SOURCE, subjectSource, errorMessage));
  }
}
