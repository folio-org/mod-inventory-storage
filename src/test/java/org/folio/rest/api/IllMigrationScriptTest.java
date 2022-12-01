package org.folio.rest.api;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import io.vertx.core.json.JsonObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class IllMigrationScriptTest extends MigrationTestBase {
  private static final String MIGRATION_SCRIPT = loadScript("updateIllPolicyWillNotLend.sql");
  private static final String WILL_NOT_LEND_ID = "b0f97013-87f5-4bab-87f2-ac4a5191b489";
  private static final String WILL_NOT_LEND_UPDATED_NAME = "Will not lend";

  @Test
  public void canMigrateIllPolicies() throws Exception {
    Map<String, JsonObject> initialIllPolicies = new HashMap<String, JsonObject>();
    illPoliciesClient.getAll().forEach(policy -> {
      initialIllPolicies.put(policy.getString("id"), policy);
    });

    executeMultipleSqlStatements(MIGRATION_SCRIPT);

    List<JsonObject> updatedIllPolicies = illPoliciesClient.getAll();

    updatedIllPolicies.forEach(policy -> {
      JsonObject originalPolicy = initialIllPolicies.get(policy.getString("id"));
      //did our special policy get updated to the correct value?
      if (policy.getString("id").contentEquals(WILL_NOT_LEND_ID)) {
        assertThat(policy.getString("name"), is(WILL_NOT_LEND_UPDATED_NAME));
      } else {
        //are all the other policy names left unchanged?
        assertThat(policy.getString("name"), is(originalPolicy.getString("name")));
      }
      //do all the policies still have their original sources?
      assertThat(policy.getString("source"), is(originalPolicy.getString("source")));
    });
  }
}
