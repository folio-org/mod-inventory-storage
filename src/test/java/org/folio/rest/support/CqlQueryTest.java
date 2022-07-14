package org.folio.rest.support;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class CqlQueryTest {

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
    assertThat(new CqlQuery(cql).isMatchingAll(), is(true));
  }

  @Parameters({
    "someid=*",
    "a=1 AND id=*",
    "id==* AND b=2",
    "cql.allRecords=1 NOT c=3",
    "d=4 NOT id==\"*\"",
    "id<>*",
    "id==a*",
    "id==*a",
    "id==\"a*\"",
    "id==\"*a\"",
    "=",
  })
  @Test
  public void doesntMatchAllItems(String cql) {
    assertThat(new CqlQuery(cql).isMatchingAll(), is(false));
  }

}
