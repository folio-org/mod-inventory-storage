package org.folio.utility;

public final class RestUtility {

  public static final String TENANT_ID = "test";
  public static final String CONSORTIUM_ID = "0060045d-35a6-4935-a923-641bc135a47d";
  public static final String CONSORTIUM_MEMBER_TENANT = "member";
  public static final String CONSORTIUM_CENTRAL_TENANT = "central";

  public static final String USER_TENANTS_PATH = "/user-tenants?limit=1";

  private RestUtility() {
    throw new UnsupportedOperationException("Cannot instantiate utility class.");
  }
}
