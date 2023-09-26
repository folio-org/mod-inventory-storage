package org.folio.rest.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.sqlclient.Tuple;
import org.folio.postgres.testing.PostgresTesterContainer;
import org.folio.rest.persist.PostgresClient;
import org.folio.util.ResourceUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.marc4j.callnum.DeweyCallNumber;
import org.marc4j.callnum.LCCallNumber;
import org.marc4j.callnum.NlmCallNumber;

@ExtendWith(VertxExtension.class)
class WriteSqlTest {
  private static Vertx vertx;

  @BeforeAll
  static void beforeAll(Vertx vertx, VertxTestContext vtc) {
    WriteSqlTest.vertx = vertx;
    PostgresClient.setPostgresTester(new PostgresTesterContainer());
    var sql = "SET search_path TO 'public';\n"
        + ResourceUtil.asString("/templates/db_scripts/instance-hr-item/write.sql");
    runSql(sql)
      .onComplete(vtc.succeedingThenComplete());
  }

  static Future<Void> runSql(String sql) {
    return PostgresClient.getInstance(vertx).runSQLFile(sql, true)
        .compose(errors -> {
          if (errors.isEmpty()) {
            return Future.succeededFuture();
          }
          return Future.failedFuture(errors.get(0));
        });
  }

  @AfterAll
  static void afterAll() {
    PostgresClient.stopPostgresTester();
  }

  @ParameterizedTest
  @CsvSource({
    // callNumber, volume, enumeration, chronology, copyNumber, expected
    "cn, v, e, ch, co, 'cn v e ch co'",
    "' cn ', ' v ', ' e ', ' ch ', ' co ', 'cn v e ch co'",
    "cn, v, , ch, , 'cn v ch'",
    "cn, , e, , co, 'cn e co'",
    ", v, e, ch, co, ",
    "'  ', v, e, ch, co, ",
  })
  void setEffectiveShelvingOrder(String callNumber, String volume, String enumeration, String chronology,
      String copyNumber, String expected, VertxTestContext vtc) {

    var jsonObject = new JsonObject().put("effectiveShelvingOrder", "old");
    if (callNumber != null) {
      jsonObject.put("effectiveCallNumberComponents", new JsonObject().put("callNumber", callNumber));
    }
    if (volume != null) {
      jsonObject.put("volume", volume);
    }
    if (enumeration != null) {
      jsonObject.put("enumeration", enumeration);
    }
    if (chronology != null) {
      jsonObject.put("chronology", chronology);
    }
    if (copyNumber != null) {
      jsonObject.put("copyNumber", copyNumber);
    }
    PostgresClient.getInstance(vertx)
      .selectSingle("SELECT public.set_effective_shelving_order($1)", Tuple.of(jsonObject))
      .onComplete(vtc.succeeding(r -> {
        assertThat(r.getJsonObject(0).getString("effectiveShelvingOrder"), is(expected));
        vtc.completeNow();
      }));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    // valid LC call number
    "A1 B2 .C33",
    "A1 B2 C33",
    "A1 B2.C33",
    "A1 B2C33",
    "AB9 L3",
    "BF199",
    "BF199.",
    "BF199.A1J7",
    "G3841 .C2 1935 .M3",
    "HC241.25 .I4 D47",
    "HD 38.25.F8 R87 1989",
    "HD38.25.F8 R87 1989",
    "HE5.215 .N9/PT.A",
    "HF 5549.5.T7 B294 1992",
    "LD6329 1903 35TH",
    "LD6353 1886",
    "M1 .L33",
    "M1 L33",
    "M5 .L",
    "M5 L3 1902",
    "M5 L3 1902 V.2",
    "M5 L3 1902 V2",
    "M5 .L3 1902 V2 TANEYTOWN",
    "M211 .M93 BMW240",
    "M211 .M93 K.240",
    "M211 .M93 K.240 1988 .A1",
    "M211 .M93 K.240 1988 A1",
    "M453 .Z29 Q1 L V.2",
    "M857 .K93 H2 OP.79",
    "M857 .M93 S412B M",
    "M1001 .H",
    "M1001 .M9 1900Z",
    "M1001 .M9 K.173D B",
    "M1001 .M9 K.551 1900Z M",
    "M1001 .M939 S.3,13 2001",
    "ML410 .M8 L25 .M95 1995",
    "ML410 .M8 L25 M95 1995",
    "ML410 .M9 P29 1941 M",
    "MT37 2003M384",
    "MT130 .M93 K96 .W83 1988",
    "MT130 .M93 K96 W83 1988",
    "PQ2678.K26 P54",
    "PQ8550.21.R57 V5 1992",
    "PR92 .L33 1990",
    "PR919 .L33 1990",
    "PR9199 .A39",
    "PR9199.48 .B3",
    "PS153 .G38 B73 2012",
    "QA76",
    "M1",
    "BF1999.A63880 1978",
    "BF1999 Aarons",
    "bq1270",
    "l666 15th A8",
    // valid NLM (National Library of Medicine) call number
    "QZ1 B2 .C33",
    // "W1 B2 .C33",  // bug in marc4j: https://github.com/marc4j/marc4j/pull/97
    "WR1 B2 .C33",
    // invalid:
    "",
    "I1 B2 .C33",
    "O1 B2 .C33",
    "Q 11 .GA1 E53 2005",
    "QSS 11 .GA1 E53 2005",
    "WAA 102.5 B5315 2018",
    "X1 B2 .C33",
    "Y1 B2 .C33",
    "Sony PDX10",
    "RCA Jz(1)",
    "XXKD671.G53 2012",
  })
  void lcNlmCallNumber(String s, VertxTestContext vtc) {
    PostgresClient.getInstance(vertx).selectSingle("SELECT public.lc_nlm_call_number($1)", Tuple.of(s))
      .onComplete(vtc.succeeding(r -> {
        var nlm = new NlmCallNumber(s);
        var lc = new LCCallNumber(s);
        if (nlm.isValid()) {
          var expected = nlm.getShelfKey();
          assertThat(s + " -> " + expected + " (NLM)", r.getString(0), is(expected));
        } else if (lc.isValid()) {
          var expected = lc.getShelfKey();
          assertThat(s + " -> " + expected + " (LC)", r.getString(0), is(expected));
        } else {
          assertThat(s + " -> null (for invalid)", r.getString(0), is(nullValue()));
        }
        vtc.completeNow();
      }));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "1 .I39",
    "1.23 .I39",
    "11 .I39",
    "11.34 .I39",
    "11.34567 .I39",
    "111 .I39",
    "111 I39",
    "111Q39",
    "111.12 .I39",
    "111.123 I39",
    "111.134Q39",
    "322.44 .F816 V.1 1974",
    "322.45 .R513 1957",
    "323 .A512RE NO.23-28",
    "323 .A778 ED.2",
    "323.09 .K43 V.1",
    "324.54 .I39 F",
    "324.548 .C425R",
    "324.6 .A75CUA",
    "341.7/58 / 21",
    "394.1 O41b",
    "9A2 C0444218 Music CD",
    // invalid:
    "",
    "MC1 259",
    "T1 105",
  })
  void deweyCallNumber(String s, VertxTestContext vtc) {
    PostgresClient.getInstance(vertx).selectSingle("SELECT public.dewey_call_number($1)", Tuple.of(s))
      .onComplete(vtc.succeeding(r -> {
        var d = new DeweyCallNumber(s);
        if (d.isValid()) {
          var expected = d.getShelfKey();
          assertThat(s + " -> " + expected, r.getString(0), is(expected));
        } else {
          assertThat(s + " -> null (for invalid)", r.getString(0), is(nullValue()));
        }
        vtc.completeNow();
      }));
  }
}
