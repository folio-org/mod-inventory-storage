package org.folio.inventory.parsing

import org.junit.Test

import java.util.regex.Pattern

class ModsParsingExamples {

  @Test
  void multipleModsRecordCanBeParsedIntoItems() {

    def modsXml = this.getClass()
      .getResourceAsStream('/mods/multiple-example-mods-records.xml')
      .getText("UTF-8")

    def records = new ModsParser(new UTF8LiteralCharacterEncoding())
      .parseRecords(modsXml);

    assert records.size() == 8

    records.each {
      println "Title: ${it.title}"
      println "Barcode: ${it.barcode}"
    }

    NoTitlesContainEscapedCharacters(records)

    assert records.any({
      similarTo(it,
        "California: its gold and its inhabitants",
        "69228882")
    })

    assert records.any({
      similarTo(it,
        "Studien zur Geschichte der Notenschrift.",
        "69247446")
    })

    assert records.any({
      similarTo(it,
        "Essays on C.S. Lewis and George MacDonald",
        "53556908")
    })

    assert records.any({
      similarTo(it,
        "Statistical sketches of Upper Canada, for the use of emigrants",
        "69077747")
    })

    assert records.any({
      similarTo(it,
        "Edward McGuire, RHA",
        "22169083")
    })

    assert records.any({
      similarTo(it,
        "Influenza della Poesia sui Costumi",
        "43620390")
    })

    assert records.any({
      similarTo(it,
        "Pavle Nik Nikitović",
        "37696876")
    })

    assert records.any({
      similarTo(it,
        "Grammaire comparée du grec et du latin",
        "69250051")
    })
  }

  private void NoTitlesContainEscapedCharacters(records) {

    assert !records.any {
      Pattern.compile(
        '(\\\\x[0-9a-fA-F]{2})+',
        Pattern.CASE_INSENSITIVE).matcher(it.title).find()
    }
  }

  private boolean similarTo(
    record,
    String expectedSimilarTitle,
    String expectedBarcode) {

      record.title.contains(expectedSimilarTitle) &&
      record.barcode == expectedBarcode
  }
}


