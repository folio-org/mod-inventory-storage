package org.folio.rest.impl;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import org.folio.rest.impl.InstanceStorageAPI.PreparedCQL;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;
import org.folio.cql2pgjson.exception.FieldException;
import io.vertx.ext.unit.junit.VertxUnitRunner;

/**
 * @see org.folio.rest.api.InstanceStorageTest
 */
@RunWith(VertxUnitRunner.class)
public class InstanceStorageAPITest {
  @Rule
  public MockitoRule mockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);
  @Rule
  public Timeout timeoutRule = Timeout.seconds(5);

  private InstanceStorageAPI instanceStorageApi = new InstanceStorageAPI();

  private void handleCQL(String cql, String sql, String table) {
    try {
      PreparedCQL preparedCql = instanceStorageApi.handleCQL(cql, 1, 0);
      String actualSql = preparedCql.getCqlWrapper().toString();
      actualSql = actualSql
          .substring(" WHERE ".length(), actualSql.length() - " LIMIT 1 OFFSET 0".length())
          .replace("(", "").replace(")", "");
      assertThat(actualSql, is(sql));
      assertThat(preparedCql.getTableName(), is(table));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void handleCQLBasic() throws FieldException {
    handleCQL("a=\"\"", "instance.jsonb->>'a' ~ ''", "instance");
    handleCQL("holdingsRecords.a=\"\"", "instance_holding_view.ho_jsonb->>'a' ~ ''", "instance_holding_view");
    handleCQL("item.a=\"\"", "instance_holding_item_view.it_jsonb->>'a' ~ ''", "instance_holding_item_view");
  }

  @Test
  public void handleCQLBasic2() throws FieldException {
    handleCQLBasic();
    handleCQLBasic();
  }

  @Test
  public void handleCQLMulti1() throws FieldException {
    handleCQL("holdingsRecords.a=\"\" AND item.b=\"\"",
              "instance_holding_item_view.ho_jsonb->>'a' ~ '' AND "
            + "instance_holding_item_view.it_jsonb->>'b' ~ ''",
              "instance_holding_item_view");
  }

  @Test
  public void handleCQLMulti2() throws FieldException {
    handleCQL("a=\"\" AND holdingsRecords.b=\"\" AND item.c=\"\" AND "
            + "d=\"\" AND holdingsRecords.e=\"\" AND item.f=\"\"",
              "instance_holding_item_view.jsonb->>'a' ~ '' AND "
            + "instance_holding_item_view.ho_jsonb->>'b' ~ '' AND "
            + "instance_holding_item_view.it_jsonb->>'c' ~ '' AND "
            + "instance_holding_item_view.jsonb->>'d' ~ '' AND "
            + "instance_holding_item_view.ho_jsonb->>'e' ~ '' AND "
            + "instance_holding_item_view.it_jsonb->>'f' ~ ''",
              "instance_holding_item_view");
  }

  @Test
  public void handleCQLMulti3() throws FieldException {
    handleCQL("holdingsRecords.a=\"\" AND b=\"\" AND "
            + "holdingsRecords.c=\"\" AND d=\"\"",
              "instance_holding_view.ho_jsonb->>'a' ~ '' AND "
            + "instance_holding_view.jsonb->>'b' ~ '' AND "
            + "instance_holding_view.ho_jsonb->>'c' ~ '' AND "
            + "instance_holding_view.jsonb->>'d' ~ ''",
              "instance_holding_view");
  }
}
