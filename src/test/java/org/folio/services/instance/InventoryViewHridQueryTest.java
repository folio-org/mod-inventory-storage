package org.folio.services.instance;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class InventoryViewHridQueryTest {

  @ParameterizedTest
  @ValueSource(strings = {
    "instance.hrid==in00000000001",
    "(instance.hrid==in00000000001)",
    "instance.hrid=in00000000001",
    "(instance.hrid=in00000000001)",
    "instanceHrid==in00000000001",
    "instance.hrid==\"in00000000001\""
  })
  @DisplayName("should return the hrid when the query is a single hrid term")
  void shouldReturnHrid_whenQueryIsSingleHridTerm(String cql) {
    assertThat(InventoryViewHridQuery.extractHrid(cql)).contains("in00000000001");
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "instance.hrid==in0000*",
    "instance.hrid==in??1",
    "instance.hrid==in^1",
    "instance.hrid==in\\*1",
    "instance.hrid==in1 and instance.discoverySuppress<>true",
    "instance.hrid==in1 or instance.hrid==in2",
    "not instance.hrid==in1",
    "instance.title==x",
    "hrid==in1",
    "id==11111111-1111-1111-1111-111111111111",
    "instance.hrid<>in1",
    "instance.hrid adj in1",
    "instance.hrid all in1",
    "instance.hrid any in1",
    "instance.hrid>in1",
    "instance.hrid=/respectCase in1",
    "instance.hrid==\"\"",
    "cql.allRecords=1",
    "instance.hrid==in1 sortby id",
    "instance.hrid==",
    "((("
  })
  @DisplayName("should return empty when the query is not a single exact hrid term")
  void shouldReturnEmpty_whenQueryIsNotSingleExactHridTerm(String cql) {
    assertThat(InventoryViewHridQuery.extractHrid(cql)).isEmpty();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = "   ")
  @DisplayName("should return empty when the query is blank")
  void shouldReturnEmpty_whenQueryIsBlank(String cql) {
    assertThat(InventoryViewHridQuery.extractHrid(cql)).isEmpty();
  }
}
