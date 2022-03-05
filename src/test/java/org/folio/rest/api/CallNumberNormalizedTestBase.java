package org.folio.rest.api;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@RunWith(JUnitParamsRunner.class)
public abstract class CallNumberNormalizedTestBase extends TestBaseWithInventoryUtil {

  protected abstract List<String> searchByCallNumberNormalized(String callNumber) throws Exception;

  @Test
  public void cannotSearchByNonAlphanumericsOnly() throws Exception {
    assertThat(searchByCallNumberNormalized("++"), hasSize(0));
  }

  @Parameters({
    "Curriculum Materials Collection",
    "curriculum materials",
    "dictionary"
  })
  @Test
  public void cannotSearchBySuffixOnly(String searchQuery) throws Exception {
    assertThat(searchByCallNumberNormalized(searchQuery), hasSize(0));
  }

  protected static String[][] callNumberData() {
    // Prefix, call number, suffix
    return new String[][] {
      {null, "AD 12", null},
      {null, "AD 120", null},
      {null, "AD 10: 1/27", null},
      {null, "AD 101: 2/7", null},
      {null, "1—2", null},
      {null, "1 . 2", null},
      {null, "1 . . 2", null},
      {null, "1— —2", null},
      {"H", "Germ 350/35: 1", null},
      {"H", "Germ 350/35: 2", null},
      {"F", "Germ 350/1", null},
      {"Oversize", "ABC123.1 .R15 2018", null},
      {"++ Oversize", "BX1935 .A23 1959", null},
      {"Rare Books", "S537.N56 C82", "++"},
      {"Rare Books", "S537.N56 C82", "Foo"},
      {null, "GE77 .F73 2014", "Curriculum Materials Collection"},
      {"Foo", "QE423 .T66", null},
      {"Foo", "QE423 .T66", "Dictionary"},
    };
  }
}
