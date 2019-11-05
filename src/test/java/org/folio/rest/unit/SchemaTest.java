package org.folio.rest.unit;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import java.io.IOException;

import org.folio.util.IoUtil;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.json.JsonObject;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@RunWith(JUnitParamsRunner.class)
public class SchemaTest {
  /**
   * Are the two schema files (without and with required id) in sync?
   * The id requirement and the javaType are the only allowed differences.
   */
  @Test
  @Parameters({
    "ramls/instance.json,       ramls/instance-with-id.json",
    "ramls/holdingsrecord.json, ramls/holdingsrecord-with-id.json",
    "ramls/item.json,           ramls/item-with-id.json",
  })
  public void schemasWithIdAreInSync(String fileWithoutId, String fileWithId) throws IOException {
    JsonObject withoutId = new JsonObject(IoUtil.toStringUtf8(fileWithoutId));
    JsonObject withId    = new JsonObject(IoUtil.toStringUtf8(fileWithId));
    withoutId.remove("javaType");
    withId.remove("javaType");
    assertThat(withoutId.getJsonArray("required").contains("id"), is(false));
    assertThat(withId   .getJsonArray("required").contains("id"), is(true));
    withId.getJsonArray("required").remove("id");
    assertThat("Schemas " + fileWithId + " and " + fileWithoutId + " should be in sync",
        withoutId, is(withId));
  }

  /**
   * Are the two schema files (without and with "totalRecords" property) in sync?
   * The "totalRecords" property is the only allowed difference.
   */
  @Test
  @Parameters({
    "ramls/examples/instances_get.json,       ramls/examples/instances-with-id.json",
    "ramls/examples/holdingsrecords_get.json, ramls/examples/holdingsrecords-with-id.json",
    "ramls/examples/items_get.json,           ramls/examples/items-with-id.json",
  })
  public void schemasWithCountAreInSync(String fileWithCount, String fileWithoutCount) throws IOException {
    JsonObject withoutCount = new JsonObject(IoUtil.toStringUtf8(fileWithoutCount));
    JsonObject withCount    = new JsonObject(IoUtil.toStringUtf8(fileWithCount));
    assertThat(withoutCount.containsKey("totalRecords"), is(false));
    assertThat(withCount   .containsKey("totalRecords"), is(true));
    withCount.remove("totalRecords");
    assertThat("Schemas " + fileWithCount + " and " + fileWithoutCount + " should be in sync",
        withoutCount, is(withCount));
  }
}
