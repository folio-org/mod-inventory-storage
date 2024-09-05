package org.folio.validator;

import io.vertx.core.Context;
import io.vertx.core.Future;
import org.folio.HttpStatus;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.rest.exceptions.NotFoundException;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.services.subjectsource.SubjectSourceService;
import org.folio.services.subjecttype.SubjectTypeService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;

public final class SubjectsValidators {

  private SubjectsValidators() {}

  public static Future<Instance> refuseIfSubjectIdsNotFound(Instance instance, Context context,
                                                            Map<String, String> okapiHeaders) {
    List<Future<Void>> futures = new ArrayList<>();
    instance.getSubjects().stream()
      .forEach(subject -> {
        performValidation(subject.getSubjectTypeId(), futures,
          typeId -> refuseIfSubjectTypeNotFound(typeId, context, okapiHeaders));
        performValidation(subject.getSubjectSourceId(), futures,
          sourceId -> refuseIfSubjectSourceNotFound(sourceId, context, okapiHeaders));
      });
    return GenericCompositeFuture.all(futures)
      .map(instance)
      .recover(Future::failedFuture);
  }

  public static Future<List<Instance>> refuseIfSubjectIdsNotFound(List<Instance> instances, Context context,
                                                                  Map<String, String> okapiHeaders) {
    var futures = instances.stream()
      .map(instance -> refuseIfSubjectIdsNotFound(instance, context, okapiHeaders))
      .toList();
    return GenericCompositeFuture.all(futures)
      .map(v -> instances)
      .recover(Future::failedFuture);
  }

  private static void performValidation(String id, List<Future<Void>> futures,
                                        Function<String, Future<Void>> validationFunction) {
    if (id != null) {
      futures.add(validationFunction.apply(id));
    }
  }

  private static Future<Void> refuseIfSubjectTypeNotFound(String id, Context context,
                                                          Map<String, String> okapiHeaders) {
    return new SubjectTypeService(context, okapiHeaders)
      .getById(id)
      .compose(subjectType -> subjectType.getStatus() == HttpStatus.HTTP_NOT_FOUND.toInt()
        ? failedFuture(new NotFoundException(String.format("SubjectType with id: %s not found", id)))
        : succeededFuture());
  }

  private static Future<Void> refuseIfSubjectSourceNotFound(String id, Context context,
                                                            Map<String, String> okapiHeaders) {
    return new SubjectSourceService(context, okapiHeaders)
      .getById(id)
      .compose(subjectType -> subjectType.getStatus() == HttpStatus.HTTP_NOT_FOUND.toInt()
        ? failedFuture(new NotFoundException(String.format("SubjectSource with id: %s not found", id)))
        : succeededFuture());
  }
}
