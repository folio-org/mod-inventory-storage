package org.folio.inventory.storage.external.failure

import org.folio.inventory.storage.external.ExternalStorageCollections
import org.folio.metadata.common.domain.Failure

import static org.hamcrest.CoreMatchers.is
import static org.junit.Assert.assertThat

class ExternalItemCollectionServerErrorExamples
  extends ExternalItemCollectionFailureExamples {

  def ExternalItemCollectionServerErrorExamples() {
    super(ExternalStorageFailureSuite.useVertx(
      { new ExternalStorageCollections(it,
        ExternalStorageFailureSuite.serverErrorStorageAddress)}))
  }

  @Override
  protected check(Failure failure) {
    assertThat(failure.reason, is("Server Error"))
    assertThat(failure.statusCode, is(500))
  }
}
