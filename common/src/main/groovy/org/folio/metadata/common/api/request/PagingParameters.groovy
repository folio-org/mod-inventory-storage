package org.folio.metadata.common.api.request

class PagingParameters {

  private final Integer limit
  private final Integer offset

  def PagingParameters(Integer limit, Integer offset) {
    this.offset = offset
    this.limit = limit
  }

  static defaults() {
    new PagingParameters(10, 0)
  }

  def getLimit() {
    this.limit;
  }

  def getOffset() {
    this.offset;
  }
}
