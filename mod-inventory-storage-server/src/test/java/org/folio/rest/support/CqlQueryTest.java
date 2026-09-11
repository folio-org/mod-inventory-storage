package org.folio.rest.support;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CqlQueryTest {

  @ValueSource(strings = {
    "cql.allRecords=1",
    "  CQL.ALLrecords  =  foo  ",
    "cql.allRecords<>1",
  })
  @ParameterizedTest
  void matchesAllItems(String cql) {
    assertThat(new CqlQuery(cql).isMatchingAll(), is(true));
  }

  @ValueSource(strings = {
    "cql.allRecords=1 NOT c=3",
    "d=4 NOT cql.allRecords=1",
    "=",
  })
  @ParameterizedTest
  void doesntMatchAllItems(String cql) {
    assertThat(new CqlQuery(cql).isMatchingAll(), is(false));
  }
}
