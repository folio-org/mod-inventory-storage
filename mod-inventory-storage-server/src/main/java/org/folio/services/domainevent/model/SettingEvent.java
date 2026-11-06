package org.folio.services.domainevent.model;

public record SettingEvent(String id, String key, Object value, String tenantId) {
}
