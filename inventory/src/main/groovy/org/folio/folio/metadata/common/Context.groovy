package org.folio.metadata.common

interface Context {
  String getTenantId()

  String getToken()

  String getOkapiLocation()
  def getHeader(String header)
  def getHeader(String header, defaultValue)
  boolean hasHeader(String header)
}
