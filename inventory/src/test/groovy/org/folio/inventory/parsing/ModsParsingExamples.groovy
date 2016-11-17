package org.folio.inventory.parsing

import org.folio.inventory.org.folio.inventory.parsing.ModsParser
import org.junit.Test

class ModsParsingExamples {

  @Test
  void multipleModsRecordCanBeParsedIntoItems() {

    def modsXml = this.getClass().getResourceAsStream('/mods/multiple-example-mods-records.xml')

    def items = new ModsParser().parseRecords(modsXml);

    assert items.size() == 5

    assert items.any({
      matches(it,
        "California: its gold and its inhabitants, by the author of 'Seven years on the Slave coast of Africa'.",
        "69228882")
    })

    assert items.any({
      matches(it,
        "Studien zur Geschichte der Notenschrift.",
        "69247446")
    })

    assert items.any({
      matches(it,
        "Essays on C.S. Lewis and George MacDonald",
        "53556908")
    })

    assert items.any({
      matches(it,
        "Statistical sketches of Upper Canada, for the use of emigrants, by a backwoodsman [W. Dunlop].",
        "69077747")
    })

    assert items.any({
      matches(it,
        "Edward McGuire, RHA",
        "22169083")
    })
  }

  private boolean matches(record, String expectedTitle, String expectedBarcode) {
      record.title == expectedTitle &&
      record.barcode == expectedBarcode
  }
}


