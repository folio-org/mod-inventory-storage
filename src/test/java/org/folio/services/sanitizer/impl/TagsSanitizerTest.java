package org.folio.services.sanitizer.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.folio.rest.jaxrs.model.Tags;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TagsSanitizerTest {

  private TagsSanitizer sanitizer;

  @BeforeEach
  void setUp() {
    sanitizer = new TagsSanitizer();
  }

  @Test
  void sanitizeShouldHandleNullTags() {
    assertDoesNotThrow(() -> sanitizer.sanitize(null));
  }

  @ParameterizedTest
  @MethodSource("tagListProvider")
  void sanitizeShouldHandleTagList(List<String> input, List<String> expected) {
    var tags = new Tags();
    tags.setTagList(input);

    sanitizer.sanitize(tags);

    assertNotNull(tags.getTagList());
    if (expected.isEmpty()) {
      assertTrue(tags.getTagList().isEmpty());
    } else {
      assertEquals(expected, tags.getTagList());
    }
  }

  private static Stream<Arguments> tagListProvider() {
    return Stream.of(
      Arguments.of(
        Arrays.asList("tag1", "", "tag2", "  ", "tag3"),
        Arrays.asList("tag1", "tag2", "tag3")
      ),
      Arguments.of(null, Arrays.asList()),
      Arguments.of(new ArrayList<>(), Arrays.asList()),
      Arguments.of(Arrays.asList("valid tag", "\t", "\n", "   "), Arrays.asList("valid tag")),
      Arguments.of(Arrays.asList("", "  ", "\t", "\n"), Arrays.asList()),
      Arguments.of(Arrays.asList("tag1", "tag2", "tag3"), Arrays.asList("tag1", "tag2", "tag3")),
      Arguments.of(
        Arrays.asList("urgent", "", "   ", "important", "\t\n", "review"),
        Arrays.asList("urgent", "important", "review")
      )
    );
  }
}
