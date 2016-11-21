package org.folio.inventory.resources.ingest

import org.folio.metadata.common.domain.AsynchronousCollection

interface IngestJobCollection extends AsynchronousCollection<IngestJob> {
  def update(IngestJob ingestJob, Closure completionCallback)
}
