package org.folio.rest.api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.UUID;
import org.junit.Test;

public class PublicationPeriodMigrationTest extends MigrationTestBase {
  private static final String MIGRATION_SCRIPT =
    "SET search_path TO " + getSchemaName() + ";\n" + loadScript("populatePublicationPeriod.sql");

  TestCase[] testCases = {
    new TestCase(null, null, null, null),
    new TestCase("1990", null, 1990, null),
    new TestCase("[1990]", null, 1990, null),
    new TestCase("©1990", null, 1990, null),
    new TestCase("[1999?]", null, 1999, null),
    new TestCase("Cop 1990", null, 1990, null),
    new TestCase("[2003], ©2001", null, 2003, null),
    new TestCase("2001, 2003,", null, 2001, 2003),
    new TestCase("2001- 2003", null, 2001, 2003),
    new TestCase("between 2001 and 2003", null, 2001, 2003),
    new TestCase("©2001", "[2003]", 2001, 2003),
    new TestCase("©2001", "[2001]", 2001, null),
    new TestCase("2001", "2003", 2001, 2003),
    new TestCase("2003-2001", "2003", 2003, null),
    new TestCase("2000-2010", "2012-2020", 2000, 2020),
    new TestCase(null, "1999", null, 1999),
    new TestCase("[19uu]", null, null, null),
    new TestCase(null, "[19uu]", null, null),
    new TestCase("hafniae MCMLXX", null, null, null),
    new TestCase(null, "hafniae MCMLXX", null, null),
    };

  @Test
  public void canMigrate() throws Throwable {
    for (TestCase testCase : testCases) {
      testCase.createInstance();
    }
    // remove auto-generated publicationPeriod
    executeSql("UPDATE " + getSchemaName() + ".instance SET jsonb = jsonb - 'publicationPeriod'");

    executeMultipleSqlStatements(MIGRATION_SCRIPT);

    for (TestCase testCase : testCases) {
      testCase.assertPublicationPeriod();
    }
  }

  private static class TestCase {
    final UUID id;
    final String dateOfPublication1;
    final String dateOfPublication2;
    final Integer expectedStart;
    final Integer expectedEnd;

    TestCase(String dateOfPublication1, String dateOfPublication2,
                    Integer expectedStart, Integer expectedEnd) {
      id = UUID.randomUUID();
      this.dateOfPublication1 = dateOfPublication1;
      this.dateOfPublication2 = dateOfPublication2;
      this.expectedStart = expectedStart;
      this.expectedEnd = expectedEnd;
    }

    public void createInstance() {
      JsonArray publication = new JsonArray();
      if (dateOfPublication1 != null || dateOfPublication2 != null) {
        if (dateOfPublication1 == null) {
          publication.add(new JsonObject());
        } else {
          publication.add(new JsonObject().put("dateOfPublication", dateOfPublication1));
        }
        if (dateOfPublication2 != null) {
          publication.add(new JsonObject().put("dateOfPublication", dateOfPublication2));
        }
      }
      instancesClient.create(instance(id).put("publication", publication));
    }

    /**
     * Assert that publicationPeriod (start and end) have the expected values.
     */
    public void assertPublicationPeriod() {
      String reason = dateOfPublication1 + " -- " + dateOfPublication2;
      JsonObject json = instancesClient.getById(id).getJson();
      JsonObject publicationPeriod = json.getJsonObject("publicationPeriod");
      if (expectedStart == null && expectedEnd == null) {
        assertThat(reason, publicationPeriod, is(nullValue()));
        return;
      }
      assertThat(reason, publicationPeriod.getInteger("start"), is(expectedStart));
      assertThat(reason, publicationPeriod.getInteger("end"), is(expectedEnd));
    }
  }

}
