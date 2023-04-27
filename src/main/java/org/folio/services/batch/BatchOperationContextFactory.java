package org.folio.services.batch;

import static io.vertx.core.Future.succeededFuture;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

import io.vertx.core.Future;
import java.util.List;
import java.util.function.Function;
import org.folio.persist.AbstractRepository;

public final class BatchOperationContextFactory {
  private BatchOperationContextFactory() { }

  public static <T> Future<BatchOperationContext<T>> buildBatchOperationContext(
    boolean upsert, List<T> all, AbstractRepository<T> repository, Function<T, String> idGetter) {

    if (!upsert) {
      return succeededFuture(new BatchOperationContext<>(all, emptyList()));
    }

    return repository.getById(all, idGetter).map(found -> {
      final var toBeCreated = all.stream()
        .filter(entity -> !found.containsKey(idGetter.apply(entity)))
        .collect(toList());

      return new BatchOperationContext<>(toBeCreated, found.values());
    });
  }
}
