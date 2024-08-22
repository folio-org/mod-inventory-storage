package org.folio.rest.support.extension;

import java.util.Arrays;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

public class EnableTenantExtension implements ParameterResolver {

  @Override
  public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
    throws ParameterResolutionException {
    return parameterContext.isAnnotated(Tenants.class);
  }

  @Override
  public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
    throws ParameterResolutionException {
    Class<?> requiredTestClass = extensionContext.getRequiredTestClass();
    var annotation = requiredTestClass.getAnnotation(EnableTenant.class);
    if (annotation == null) {
      Class<?> superclass = requiredTestClass.getSuperclass();
      while (superclass != null) {
        var superclassAnnotation = superclass.getAnnotation(EnableTenant.class);
        if (superclassAnnotation != null) {
          annotation = superclassAnnotation;
          break;
        }
        superclass = superclass.getSuperclass();
      }
    }
    var tenants = annotation.tenants();
    return Arrays.asList(tenants);
  }
}
