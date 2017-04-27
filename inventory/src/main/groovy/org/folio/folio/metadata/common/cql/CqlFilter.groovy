package org.folio.metadata.common.cql

class CqlFilter {
  Closure filterBy(field, searchTerm) {
    return {
      if (searchTerm == null) {
        true
      } else {
        it."${field}".contains(searchTerm)
      }
    }
  }
}
