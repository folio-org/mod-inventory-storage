package org.folio.persist;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.vertx.core.Vertx;
import java.util.Map;
import org.folio.okapi.common.XOkapiHeaders;
import org.junit.jupiter.api.Test;

class HoldingsRepositoryTest {

  @Test
  void invalidSortBy() {
    String[] sortBys = {"foo"};
    var context = Vertx.vertx().getOrCreateContext();
    var headers = Map.of(XOkapiHeaders.TENANT, "diku");
    var holdingsRepository = new HoldingsRepository(context, headers);
    var future = holdingsRepository.getByInstanceId(null, sortBys, 0, 0);
    assertInstanceOf(IllegalArgumentException.class, future.cause());
    assertThat(future.cause().getMessage(), is("sortBy: foo"));
  }
}
