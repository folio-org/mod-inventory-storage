package org.folio.inventory.parsing

class ModsParser {
  public Set<Map<String, String>> parseRecords(String xml) {

    def modsRecord = new XmlSlurper().parseText(xml)

    def Set<Map<String, String>> records = []

    modsRecord.mods.each {
      def title = it.titleInfo.title.text()
      def barcode = it.location.holdingExternal.localHolds.objId.toString()

      def record = [:]

      record.put("title", title)
      record.put("barcode", barcode)

      records.add(record)
    }

    records
  }
}
