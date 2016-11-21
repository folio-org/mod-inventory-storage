package org.folio.inventory.resources.ingest

enum IngestJobState {
  REQUESTED("Requested"), IN_PROGRESS("In Progress"), COMPLETED("Completed")

  private final String printableDescription

  IngestJobState(String printableDescription) {
    this.printableDescription = printableDescription
  }

  @Override
  public String toString() {
    return printableDescription;
  }
}
