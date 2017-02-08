package org.folio.inventory.domain.ingest

import org.folio.inventory.domain.AsynchronousCollection
import org.folio.inventory.resources.ingest.IngestJob

interface IngestJobCollection
  extends AsynchronousCollection<IngestJob> {

}
