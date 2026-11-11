package org.folio.services.customfields;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HexFormat;
import java.util.TreeMap;
import java.util.jar.JarFile;
import org.folio.service.CustomFieldsServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the database scripts in the folio-custom-fields jar, see the {@code custom_fields} table changeset in
 * {@code liquibase/tenant/scripts/v31.0.0/01-custom-fields.xml}.
 */
class CustomFieldsLibraryDbScriptsTest {

  private static final String DB_SCRIPTS = "templates/db_scripts/";
  private static final String EXPECTED_SHA256 = "b8ab81e34f81c64043f87845ecc79253d1ce85e1f31c9f0cbe59fd0c622f0559";

  @Test
  @DisplayName("should find the database scripts of folio-custom-fields unchanged")
  void shouldFindLibraryDbScriptsUnchanged() throws Exception {
    var listing = libraryDbScripts();

    assertThat(sha256(listing.getBytes(UTF_8)))
      .as("folio-custom-fields changed its database scripts. Check the library release for migrations that "
        + "existing tenants need, add them as Liquibase changesets after the custom_fields table changeset, "
        + "then update the hash in this test. The scripts now:%n%s", listing)
      .isEqualTo(EXPECTED_SHA256);
  }

  /** One line per file, sorted by name: the file name and the SHA-256 of its content. */
  private static String libraryDbScripts() throws Exception {
    var jar = Path.of(CustomFieldsServiceImpl.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    var scripts = new TreeMap<String, String>();
    try (var jarFile = new JarFile(jar.toFile())) {
      for (var entry : Collections.list(jarFile.entries())) {
        if (entry.isDirectory() || !entry.getName().startsWith(DB_SCRIPTS)) {
          continue;
        }
        try (var content = jarFile.getInputStream(entry)) {
          scripts.put(entry.getName().substring(DB_SCRIPTS.length()), sha256(content.readAllBytes()));
        }
      }
    }
    var listing = new StringBuilder();
    scripts.forEach((name, hash) -> listing.append(name).append(' ').append(hash).append('\n'));
    return listing.toString();
  }

  private static String sha256(byte[] bytes) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }
}
