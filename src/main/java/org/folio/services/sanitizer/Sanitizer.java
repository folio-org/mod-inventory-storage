package org.folio.services.sanitizer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface Sanitizer<T> {

  void sanitize(@Nullable T entity);

  default List<String> cleanList(@Nullable List<String> list) {
    if (list == null) {
      return new ArrayList<>();
    }
    return list.stream()
      .filter(StringUtils::isNotBlank)
      .toList();
  }

  default Set<String> cleanSet(@Nullable Set<String> set) {
    if (set == null) {
      return new LinkedHashSet<>();
    }
    return set.stream()
      .filter(StringUtils::isNotBlank)
      .collect(Collectors.toCollection(LinkedHashSet::new));
  }
}
