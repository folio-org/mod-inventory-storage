package org.folio.metadata.common.api.request

import org.apache.commons.lang.StringUtils
import org.folio.metadata.common.WebContext

class PagingParameters {

  private final Integer limit
  private final Integer offset

  def PagingParameters(Integer limit, Integer offset) {
    this.offset = offset
    this.limit = limit
  }

  static PagingParameters defaults() {
    new PagingParameters(10, 0)
  }

  static PagingParameters from(WebContext context) {

    def limit = context.getStringParameter("limit", "10")
    def offset = context.getStringParameter("offset", "0")

    if(valid(limit, offset)) {
      new PagingParameters(Integer.parseInt(limit), Integer.parseInt(offset))
    }
    else {
      null
    }
  }

  def getLimit() {
    this.limit;
  }

  def getOffset() {
    this.offset;
  }

  private static boolean valid(String limit, String offset) {
    if(StringUtils.isEmpty(limit) || StringUtils.isEmpty(offset)) {
      false
    }
    else {
      StringUtils.isNumeric(limit) && StringUtils.isNumeric(offset)
    }
  }
}
