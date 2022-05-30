package org.folio.rest.support;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.folio.rest.testing.UtilityClassTester;
import org.junit.Test;
import org.junit.runner.RunWith;
@RunWith(JUnitParamsRunner.class)

public class CqlUtilTest {

  @Test
  public void isUtilityClass() {
    UtilityClassTester.assertUtilityClass(CqlUtil.class);
  }

  @Parameters({
    "id=*",
    "id=\"*\"",
    "id==*",
    "id==\"*\"",
    "  id  =  *  ",
    "  id  =  \"*\"  ",
    "  id  ==  *  ",
    "  id  ==  \"*\"  ",
    "cql.allRecords=1",
    "  cql.allRecords  =  1  ",
  })
  @Test
  public void matchesAllItems(String cql) {
    assertThat(CqlUtil.isMatchingAll(cql), is(true));
  }

  @Parameters({
    "someid=*",
    "a=1 AND id=*",
    "id==* AND b=2",
    "cql.allRecords=1 NOT c=3",
    "d=4 NOT id==\"*\"",
    "id==a*",
    "id==*a",
    "id==\"a*\"",
    "id==\"*a\"",
  })
  @Test
  public void doesntMatchAllItems(String cql) {
    assertThat(CqlUtil.isMatchingAll(cql), is(false));
  }

}
