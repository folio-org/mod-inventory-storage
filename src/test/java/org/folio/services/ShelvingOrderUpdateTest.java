package org.folio.services;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import static org.folio.rest.api.StorageTestSuite.removeTenant;
import static org.folio.rest.api.StorageTestSuite.tenantOp;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import lombok.SneakyThrows;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.folio.rest.api.TestBase;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.support.ShelvingOrderUpdate;

@RunWith(VertxUnitRunner.class)
public class ShelvingOrderUpdateTest extends TestBase {

  private static final Logger log = LogManager.getLogger();

  private static final String VERSION_WITH_SHELVING_ORDER = "mod-inventory-storage-20.1.0";
  private static final String FROM_MODULE_DO_UPGRADE = "mod-inventory-storage-19.9.0";
  private static final String FROM_MODULE_SKIP_UPGRADE = "mod-inventory-storage-20.2.0";

  private static String defaultTenant;
  private static ShelvingOrderUpdate shelvingOrderUpdate;

  @BeforeClass
  @SneakyThrows
  public static void beforeClass(TestContext context) {
    shelvingOrderUpdate = ShelvingOrderUpdate.getInstance();
    defaultTenant = generateTenantValue();
    tenantOp(defaultTenant,
        JsonObject.mapFrom(new TenantAttributes().withModuleTo((VERSION_WITH_SHELVING_ORDER))));
  }

  @AfterClass
  @SneakyThrows
  public static void afterClass(TestContext context) {
    deleteTenant(defaultTenant);
  }

  @Test
  public void checkingShouldPassForOlderModuleVersions() {
    shelvingOrderUpdate = ShelvingOrderUpdate.getInstance();
    defaultTenant = generateTenantValue();

    final TenantAttributes tenantAttributes = getTenantAttributes(FROM_MODULE_DO_UPGRADE);
    boolean result = shelvingOrderUpdate.isAllowedToUpdate(tenantAttributes);
    assertTrue("Module version eligible to upgrade", result);
  }

  @Test
  public void checkingShouldFailForNewerModuleVersions() {
    shelvingOrderUpdate = ShelvingOrderUpdate.getInstance();
    defaultTenant = generateTenantValue();

    final TenantAttributes tenantAttributes = getTenantAttributes(FROM_MODULE_SKIP_UPGRADE);
    boolean result = shelvingOrderUpdate.isAllowedToUpdate(tenantAttributes);
    assertFalse("Module version isn't eligible to upgrade", result);
  }

  @Test
  public void checkingShouldFailForEmptyModuleVersionPassed() {
    shelvingOrderUpdate = ShelvingOrderUpdate.getInstance();
    defaultTenant = generateTenantValue();

    final TenantAttributes tenantAttributes = getTenantAttributes(StringUtils.EMPTY);
    boolean result = shelvingOrderUpdate.isAllowedToUpdate(tenantAttributes);
    assertFalse("Empty module version isn't eligible to upgrade", result);
  }

  @Test
  public void checkingShouldFailForNullModuleVersionPassed() {
    shelvingOrderUpdate = ShelvingOrderUpdate.getInstance();
    defaultTenant = generateTenantValue();

    final TenantAttributes tenantAttributes = getTenantAttributes(null);
    boolean result = shelvingOrderUpdate.isAllowedToUpdate(tenantAttributes);
    assertFalse("Empty module version isn't eligible to upgrade", result);
  }

  @Test
  public void checkingShouldFailForMissedModuleVersionPassed() {
    shelvingOrderUpdate = ShelvingOrderUpdate.getInstance();
    defaultTenant = generateTenantValue();

    final TenantAttributes tenantAttributes = new TenantAttributes();
    boolean result = shelvingOrderUpdate.isAllowedToUpdate(tenantAttributes);
    assertFalse("Request without specified module version isn't eligible to upgrade", result);
  }

  @Test
  public void shouldSucceedItemsUpdateForExpectedModuleVersion() {
    String tenant = generateTenantValue();
    log.info("Tenant generated: {}", tenant);

    log.info("Tenant initialization started: {}", tenant);
    initTenant(tenant);
    log.info("Tenant initialization finished");

    log.info("Module upgrade started, fromModuleVersion: {}", FROM_MODULE_DO_UPGRADE);
    postTenantOperation(tenant, FROM_MODULE_DO_UPGRADE);
    log.info("Module upgrade completed");

    log.info("Tenant deletion started: {}", tenant);
    deleteTenant(tenant);
    log.info("Tenant deletion finished");
  }

  @Test
  public void shouldFailItemsUpdateForUnexpectedModuleVersion() {
    String tenant = generateTenantValue();
    log.info("Tenant generated: {}", tenant);

    log.info("Tenant initialization started: {}", tenant);
    initTenant(tenant);
    log.info("Tenant initialization finished");

    log.info("Module upgrade started, fromModuleVersion: {}", FROM_MODULE_SKIP_UPGRADE);
    postTenantOperation(tenant, FROM_MODULE_SKIP_UPGRADE);
    log.info("Module upgrade completed");

    log.info("Tenant deletion started: {}", tenant);
    deleteTenant(tenant);
    log.info("Tenant deletion finished");
  }

  private static TenantAttributes getTenantAttributes(String moduleFrom) {
    return new TenantAttributes()
        .withModuleTo(VERSION_WITH_SHELVING_ORDER)
        .withModuleFrom(moduleFrom);
  }

  private static String generateTenantValue() {
    String tenant = String.format("tenant_%s", RandomStringUtils.randomAlphanumeric(7));
    log.info("New tenant generated: {}", tenant);
    return tenant;
  }

  @SneakyThrows
  private static void postTenantOperation(String tenant, String fromModuleValue) {
    tenantOp(tenant, JsonObject.mapFrom(getTenantAttributes(fromModuleValue)));
  }

  @SneakyThrows
  private static void deleteTenant(String tenant) {
    removeTenant(tenant);
  }

  @SneakyThrows
  private void initTenant(String tenant) {
    tenantOp(tenant,
        JsonObject.mapFrom(new TenantAttributes().withModuleTo(VERSION_WITH_SHELVING_ORDER)));
  }

}
