package org.folio.inventory.org.folio.inventory.parsing

import org.folio.inventory.domain.Item
import org.xml.sax.InputSource

class ModsParser {
  public Set<Item> parseRecords(String xml) {

    def modsRecord = new XmlSlurper().parseText(xml)

    def Set<Item> items = []

    modsRecord.mods.each {
      def title = it.titleInfo.title.text()
      def barcode = it.location.holdingExternal.localHolds.objId.toString()

      def item = new Item(UUID.randomUUID().toString(), title, barcode)

      items.add(item)
    }

    items
  }

  public Set<Item> parseRecords(InputStream xmlStream) {

    def source = new InputSource(xmlStream)
    source.setEncoding('UTF-8')

    def modsRecord = new XmlSlurper().parse(source)

    def Set<Item> items = []

    modsRecord.mods.each {
      def title = it.titleInfo.title.text()
      def barcode = it.location.holdingExternal.localHolds.objId.toString()

      def item = new Item(UUID.randomUUID().toString(), title, barcode)

      items.add(item)
    }

    items
  }


}
