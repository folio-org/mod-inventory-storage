package org.folio.rest.support.extension;

import static org.folio.utility.RestUtility.TENANT_ID;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ExtendWith(EnableTenantExtension.class)
public @interface EnableTenant {

  String[] tenants() default {TENANT_ID};
}
