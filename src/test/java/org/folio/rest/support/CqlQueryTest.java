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
    "cql.allRecords=1",
    "  CQL.ALLrecords  =  foo  ",
    "cql.allRecords<>1",
  })
  @Test
  public void matchesAllItems(String cql) {
    assertThat(new CqlQuery(cql).isMatchingAll(), is(true));
  }

  @Parameters({
    "cql.allRecords=1 NOT c=3",
    "d=4 NOT cql.allRecords=1",
    "=",
  })
  @Test
  public void doesntMatchAllItems(String cql) {
    assertThat(new CqlQuery(cql).isMatchingAll(), is(false));
  }

}
