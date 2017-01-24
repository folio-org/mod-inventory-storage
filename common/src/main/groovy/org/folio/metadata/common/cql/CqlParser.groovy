package org.folio.metadata.common.cql

class CqlParser {
  Tuple2<String, String> parseCql(String cqlQuery) {
    if(cqlQuery == null) {
      new Tuple2<String, String>(null, null)
    }
    else {
      def parts = cqlQuery.replaceAll("\"", "").split("=")

      if(parts.size() != 2)
        throw new IllegalArgumentException("CQL Query should have two parts")
      else {
        new Tuple2(parts[0], parts[1].replaceAll("\\*", ""))
      }
    }
  }
}
