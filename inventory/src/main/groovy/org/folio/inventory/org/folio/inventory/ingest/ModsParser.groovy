package org.folio.inventory.org.folio.inventory.ingest

import org.folio.inventory.domain.Item

class ModsParser {
  public Item parseRecord(String recordText) {
    def modsRecord = new XmlSlurper().parseText(recordText)

    def title = modsRecord.extension.modsCollection.mods.titleInfo.title.text()
    def barcode = modsRecord.extension.modsCollection.mods.location.holdingExternal.localHolds.objId.toString()

    def item = new Item(UUID.randomUUID().toString(), title, barcode)

    item
  }
}
