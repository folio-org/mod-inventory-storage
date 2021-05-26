package org.folio.rest.impl;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import junitparams.converters.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class TenantRefAPITest {
  @Parameters({
    "null  , 1.22.3, true",
    "1.3.2 , 1.22.3, true",
    "1.22.2, 1.22.3, true",
    "1.22.3, 1.22.3, false",
    "1.22.4, 1.22.3, false",
  })
  @Test
  public void isNew(@Nullable String migratingFromVersion, String featureVersion, boolean expected) {
    assertThat(new TenantRefAPI.ModuleFrom(migratingFromVersion).isNew(featureVersion), is(expected));
    assertThat(new TenantRefAPI.ModuleFrom("mod-foo-" + migratingFromVersion).isNew(featureVersion), is(expected));
  }
}