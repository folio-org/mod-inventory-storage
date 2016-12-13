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

      def record = [:]
      record.put("title", title)
      record.put("barcode", barcode)

      records.add(record)
    }

    records
  }
}
