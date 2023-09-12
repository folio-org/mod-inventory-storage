package org.folio.rest.support;

public final class ContextUtil {
  private ContextUtil() {}

  public static Context constructContext(String tenantId, String token, String okapiUrl) {
    return new Context() {
      @Override
      public String getTenantId() {
        return tenantId;
      }

      @Override
      public String getToken() {
        return token;
      }

      @Override
      public String getOkapiLocation() {
        return okapiUrl;
      }

      @Override
      public String getUserId() {
        return "";
      }
    };
  }
}
