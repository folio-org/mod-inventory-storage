package org.folio.rest.api;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;

import org.folio.rest.support.builders.StatisticalCodeBuilder;
import org.junit.Before;
import org.junit.Test;

public class StatisticalCodeTest extends TestBaseWithInventoryUtil {
  @Before
  public void removeTestStatisticalCodes() {
    statisticalCodeFixture.removeTestStatisticalCodes();
  }

  @Test
  public void cannotCreateStatisticalCodeWhenNameIsTheSameButInUpperCase() {
    final var firstStatisticalCode = new StatisticalCodeBuilder()
      .withCode("stcone")
      .withName("Statistical code");

    final var secondStatisticalCode = new StatisticalCodeBuilder()
      .withCode("stctwo")
      .withName("STATISTICAL CODE");

    statisticalCodeFixture.createSerialManagementCode(firstStatisticalCode);
    final var response = statisticalCodeFixture
      .attemptCreateSerialManagementCode(secondStatisticalCode);

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), containsString(
      "duplicate key value violates unique constraint \"statistical_code_name_idx_unique\""));
  }

  @Test
  public void canCreateStatisticalCodeWhenNamesAreDifferent() {
    final var firstStatisticalCode = new StatisticalCodeBuilder()
      .withCode("stcone")
      .withName("Statistical code 1");

    final var secondStatisticalCode = new StatisticalCodeBuilder()
      .withCode("stctwo")
      .withName("STATISTICAL CODE 2");

    final var firstCreated = statisticalCodeFixture.createSerialManagementCode(firstStatisticalCode);
    final var secondCreated = statisticalCodeFixture.createSerialManagementCode(secondStatisticalCode);

    assertThat(firstCreated.getJson().getString("name"), is("Statistical code 1"));
    assertThat(secondCreated.getJson().getString("name"), is("STATISTICAL CODE 2"));
  }
}
