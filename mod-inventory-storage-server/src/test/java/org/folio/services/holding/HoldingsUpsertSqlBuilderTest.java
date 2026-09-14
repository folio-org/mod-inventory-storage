package org.folio.services.holding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import org.folio.persist.HoldingsRepository;
import org.folio.persist.ItemRepository;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.persist.PostgresClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HoldingsUpsertSqlBuilderTest {

  private static final String HOLDINGS_TABLE = "diku_mod_inventory_storage.holdings_record";
  private static final String ITEMS_TABLE = "diku_mod_inventory_storage.item";

  private HoldingsUpsertSqlBuilder builder;

  @BeforeEach
  void setUp() {
    var holdingsRepository = mock(HoldingsRepository.class);
    var itemRepository = mock(ItemRepository.class);
    when(holdingsRepository.getFullTableName()).thenReturn(HOLDINGS_TABLE);
    when(itemRepository.getFullTableName()).thenReturn(ITEMS_TABLE);
    builder = new HoldingsUpsertSqlBuilder(holdingsRepository, itemRepository);
  }

  @Test
  @DisplayName("should build a single uuid/jsonb parameter pair when given one holding")
  void shouldBuildSingleUuidJsonbParameterPair_whenGivenOneHolding() {
    var holding = new HoldingsRecord().withId("id-1");

    var result = builder.buildUpsertSqlWithParams(List.of(holding));

    assertThat(result.getRight()).isNull();
    var sql = result.getLeft().getKey();
    assertThat(sql)
      .contains("WITH upsert_data AS (SELECT $1::uuid as id, $2::jsonb as data)")
      .doesNotContain("$3::uuid");
    assertThat(result.getLeft().getValue().size()).isEqualTo(2);
    assertThat(result.getLeft().getValue().getString(0)).isEqualTo("id-1");
  }

  @Test
  @DisplayName("should join records with union all and increment parameter indexes when given multiple holdings")
  void shouldJoinRecordsWithUnionAllAndIncrementParams_whenGivenMultipleHoldings() {
    var holdings = List.of(new HoldingsRecord().withId("id-1"), new HoldingsRecord().withId("id-2"));

    var result = builder.buildUpsertSqlWithParams(holdings);

    assertThat(result.getRight()).isNull();
    var sql = result.getLeft().getKey();
    assertThat(sql)
      .contains("SELECT $1::uuid as id, $2::jsonb as data")
      .contains("UNION ALL")
      .contains("SELECT $3::uuid as id, $4::jsonb as data");
    assertThat(result.getLeft().getValue().size()).isEqualTo(4);
  }

  @Test
  @DisplayName("should reference the repositories' full table names when building the query")
  void shouldReferenceRepositoriesFullTableNames_whenBuildingQuery() {
    var holding = new HoldingsRecord().withId("id-1");

    var result = builder.buildUpsertSqlWithParams(List.of(holding));

    var sql = result.getLeft().getKey();
    assertThat(sql)
      .contains("FROM " + HOLDINGS_TABLE)
      .contains("UPDATE " + HOLDINGS_TABLE)
      .contains("INSERT INTO " + HOLDINGS_TABLE)
      .contains("FROM " + ITEMS_TABLE);
  }

  @Test
  @DisplayName("should end with a select of the combined results")
  void shouldEndWithSelectOfCombinedResults_whenBuildingQuery() {
    var holding = new HoldingsRecord().withId("id-1");

    var result = builder.buildUpsertSqlWithParams(List.of(holding));

    assertThat(result.getLeft().getKey())
      .endsWith("SELECT id, old_holdings_content, old_item_content FROM combined_results");
  }

  @Test
  @DisplayName("should return the serialization error instead of sql when a holding cannot be serialized")
  void shouldReturnSerializationError_whenHoldingCannotBeSerialized() {
    var holding = new HoldingsRecord().withId("id-1");
    var cause = mock(JsonProcessingException.class);
    try (var pgClientMock = mockStatic(PostgresClient.class)) {
      pgClientMock.when(() -> PostgresClient.pojo2JsonObject(holding)).thenThrow(cause);

      var result = builder.buildUpsertSqlWithParams(List.of(holding));

      assertThat(result.getLeft()).isNull();
      assertThat(result.getRight()).isSameAs(cause);
    }
  }
}
