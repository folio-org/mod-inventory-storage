package org.folio.services.migration.async;

import org.apache.logging.log4j.LogManager;

/**
 * Settings shared by all state-table background migrations.
 *
 * @param chunkSize number of source records per chunk
 * @param workMemMb value of work_mem in MB for the chunk transaction
 * @param pauseMs pause between chunks and between retries in milliseconds
 * @param retries number of retries of a failed chunk
 */
public record BackgroundMigrationSettings(int chunkSize, int workMemMb, long pauseMs, int retries) {

  public static BackgroundMigrationSettings fromEnvironment() {
    return new BackgroundMigrationSettings(
      readInt("BACKGROUND_MIGRATION_CHUNK_SIZE", 1000, 1),
      readInt("BACKGROUND_MIGRATION_WORK_MEM_MB", 64, 1),
      readInt("BACKGROUND_MIGRATION_PAUSE_MS", 1000, 0),
      readInt("BACKGROUND_MIGRATION_RETRIES", 2, 0));
  }

  private static int readInt(String name, int defaultValue, int minValue) {
    var value = System.getenv(name);
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    try {
      var parsed = Integer.parseInt(value.trim());
      if (parsed >= minValue) {
        return parsed;
      }
    } catch (NumberFormatException e) {
      // fall through to the default value
    }
    LogManager.getLogger(BackgroundMigrationSettings.class)
      .warn("Invalid value of {}, using default {}", name, defaultValue);
    return defaultValue;
  }
}
