package org.folio.rest.impl;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import junitparams.converters.Nullable;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class TenantRefApiTest {
  @Parameters({
    "null  , 1.22.3, true",
    "1.3.2 , 1.22.3, true",
    "1.22.2, 1.22.3, true",
    "1.22.3, 1.22.3, false",
    "1.22.4, 1.22.3, false",
  })
  @Test
  public void isNew(@Nullable String migratingFromVersion, String featureVersion, boolean expected) {
    var tenantAttributes = new TenantAttributes().withModuleFrom(migratingFromVersion);
    assertThat(TenantRefApi.isNew(tenantAttributes, featureVersion), is(expected));
    if (migratingFromVersion == null) {
      return;
    }
    tenantAttributes = new TenantAttributes().withModuleFrom("mod-foo-" + migratingFromVersion);
    assertThat(TenantRefApi.isNew(tenantAttributes, featureVersion), is(expected));
  }
}

