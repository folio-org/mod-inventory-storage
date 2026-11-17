package org.folio.services.reindex;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Map;
import java.util.Optional;
import org.junit.Test;

public class ReindexJobIdHeaderTest {

  @Test
  public void fromOkapiHeaders_returnsValue_caseInsensitive() {
    var headers = Map.of("x-okapi-reindex-job-id", "job-1");

    assertThat(ReindexJobIdHeader.fromOkapiHeaders(headers), is(Optional.of("job-1")));
  }

  @Test
  public void fromOkapiHeaders_returnsEmpty_whenMissingOrBlank() {
    assertThat(ReindexJobIdHeader.fromOkapiHeaders(null), is(Optional.empty()));
    assertThat(ReindexJobIdHeader.fromOkapiHeaders(Map.of()), is(Optional.empty()));
    assertThat(ReindexJobIdHeader.fromOkapiHeaders(Map.of(ReindexJobIdHeader.NAME, " ")), is(Optional.empty()));
  }
}
