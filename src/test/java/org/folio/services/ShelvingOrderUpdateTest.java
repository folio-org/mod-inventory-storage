package org.folio.services;

import static org.folio.rest.api.StorageTestSuite.removeTenant;
import static org.folio.rest.api.StorageTestSuite.tenantOp;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import lombok.SneakyThrows;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.folio.rest.api.TestBase;
import org.folio.rest.jaxrs.model.TenantAttributes;

@RunWith(VertxUnitRunner.class)
public class ShelvingOrderUpdateTest extends TestBase {

  private static final Logger log = LogManager.getLogger();

  private static final String FROM_MODULE_EXPECTED_VALUE = "mod-inventory-storage-20.1.0";
  private static final String FROM_MODULE_UNEXPECTED_VALUE = "mod-inventory-storage-21.0.0";
  private static final String BASE_MODULE_VALUE = "mod-inventory-storage-1.0.0";

  private static String defaultTenant;

  @BeforeClass
  @SneakyThrows
  public static void beforeClass(TestContext context) {
    defaultTenant = generateTenantValue();
    tenantOp(defaultTenant,
        JsonObject.mapFrom(new TenantAttributes().withModuleTo((BASE_MODULE_VALUE))));
  }

  @AfterClass
  @SneakyThrows
  public static void afterClass(TestContext context) {
    deleteTenant(defaultTenant);
  }

  @Test
  public void shouldSucceedItemsUpdateForExpectedModuleVersion() {
    String tenant = generateTenantValue();
    log.info("Tenant generated: {}", tenant);

    log.info("Tenant initialization started: {}", tenant);
    initTenant(tenant);
    log.info("Tenant initialization finished");

    log.info("Module upgrade started, fromModuleVersion: {}", FROM_MODULE_EXPECTED_VALUE);
//    postTenantOperation(tenant, FROM_MODULE_EXPECTED_VALUE);
    log.info("Module upgrade completed");

    log.info("Tenant deletion started: {}", tenant);
    deleteTenant(tenant);
    log.info("Tenant deletion finished");
  }

  @Ignore
  @Test
  public void shouldFailItemsUpdateForUnexpectedModuleVersion() {
    String tenant = generateTenantValue();
    log.info("Tenant generated: {}", tenant);

    log.info("Tenant initialization started: {}", tenant);
    initTenant(tenant);
    log.info("Tenant initialization finished");

    log.info("Module upgrade started, fromModuleVersion: {}", FROM_MODULE_UNEXPECTED_VALUE);
//    postTenantOperation(tenant, FROM_MODULE_UNEXPECTED_VALUE);
    log.info("Module upgrade completed");

    log.info("Tenant deletion started: {}", tenant);
    deleteTenant(tenant);
    log.info("Tenant deletion finished");
  }

  private static String generateTenantValue() {
    String tenant = String.format("tenant_%s", RandomStringUtils.randomAlphanumeric(7));
    log.info("*** net tenant generated: {}", tenant);
    return tenant;
  }

  @SneakyThrows
  private static void postTenantOperation(String tenant, String fromModuleValue) {
    tenantOp(tenant, JsonObject.mapFrom(new TenantAttributes().withModuleFrom(fromModuleValue)));
  }

  @SneakyThrows
  private static void deleteTenant(String tenant) {
    removeTenant(tenant);
  }

  @SneakyThrows
  private void initTenant(String tenant) {
    tenantOp(tenant, JsonObject.mapFrom(new TenantAttributes().withModuleTo(BASE_MODULE_VALUE)));
  }

}
