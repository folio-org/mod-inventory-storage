package org.folio.rest.support;

public interface Context {
  String getTenantId();

  String getToken();

  String getOkapiLocation();

  String getUserId();
}
