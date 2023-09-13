package org.folio.persist;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.List;
import java.util.Map;
import org.folio.cql2pgjson.exception.QueryValidationException;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
class HoldingsRepositoryTest {

  private static HoldingsRepository holdingsRepository =
      new HoldingsRepository(Vertx.vertx().getOrCreateContext(), Map.of());
  private static HoldingsRecord holdingThrowingFoo = new HoldingsRecord() {
    public String getId() {
      throw new IllegalCallerException("foo");
    }
  };

  @Test
  void upsertException(VertxTestContext vtc) {
    holdingsRepository.upsert(List.of(holdingThrowingFoo))
      .onComplete(vtc.failing(e -> {
        assertThat(e.getCause().getMessage(), is("foo"));
        vtc.completeNow();
      }));
  }

  @Test
  void deleteException(VertxTestContext vtc) {
    holdingsRepository.delete(")")
      .onComplete(vtc.failing(e -> {
        assertThat(e.getCause(), instanceOf(QueryValidationException.class));
        vtc.completeNow();
      }));
  }
}
