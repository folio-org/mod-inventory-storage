package org.folio.inventory.resources.ingest

class IngestJob {
  final String id
  final IngestJobState state

  IngestJob(String id, IngestJobState state) {
    this.id = id
    this.state = state
  }

  IngestJob(IngestJobState state) {
    this(null, state)
  }

  def IngestJob copyWithNewId(String newId) {
    new IngestJob(newId, this.state)
  }

  IngestJob complete() {
    new IngestJob(this.id, IngestJobState.COMPLETED)
  }
}
