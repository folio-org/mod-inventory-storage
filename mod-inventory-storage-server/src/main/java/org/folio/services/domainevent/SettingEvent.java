package org.folio.services.domainevent;

public record SettingEvent(String id, String key, Object value, String tenantId) {
}
