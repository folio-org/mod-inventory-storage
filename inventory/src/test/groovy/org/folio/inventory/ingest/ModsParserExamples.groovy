package org.folio.inventory.ingest

import org.folio.inventory.org.folio.inventory.ingest.ModsParser
import org.junit.Before
import org.junit.Test

class ModsParserExamples {
  def createdItem

  @Before
  void before() {
    def modsXml = this.getClass().getResource('/mods-single-record.xml').text
    createdItem = new ModsParser().parseRecord(modsXml);
  }

  @Test
  void parsedItemHasTitle() {
    assert createdItem.title == "'Edward Samuel: ei Oes a'i Waith', an essay submitted for competition at the National Eisteddfod held at Corwen, 1919; together ..., 1919."
  }

  @Test
  void parsedItemHasBarcode() {
    assert createdItem.barcode == "78584457"
  }
}
