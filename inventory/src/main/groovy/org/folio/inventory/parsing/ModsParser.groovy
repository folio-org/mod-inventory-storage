package org.folio.inventory.parsing

class ModsParser {

  private final CharacterEncoding characterEncoding

  ModsParser(CharacterEncoding characterEncoding) {
    this.characterEncoding = characterEncoding
  }

  public List<Map<String, String>> parseRecords(String xml) {

    def modsRecord = new XmlSlurper()
      .parseText(xml)

    def records = []

    modsRecord.mods.each {
      def title = characterEncoding.decode(it.titleInfo.title.text())
      def barcode = it.location.holdingExternal.localHolds.objId.toString()

      def identifiers = it.identifier.list().collect( {
        [ "namespace" : it.@type, "value" : it.text() ]
      })

      def recordIdentifiers = it.recordInfo.recordIdentifier.list().collect( {
        [ "namespace" : it.@source, "value" : it.text() ]
      })

      def record = [:]
      record.put("title", title)
      record.put("barcode", barcode)
      record.put("identifiers", recordIdentifiers + identifiers)

      records.add(record)
    }

    records
  }
}
