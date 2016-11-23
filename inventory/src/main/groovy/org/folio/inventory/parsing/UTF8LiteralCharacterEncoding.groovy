package org.folio.inventory.parsing

class UTF8LiteralCharacterEncoding implements CharacterEncoding {

  @Override
  String decode(String input) {
    input
      .replace("\\xE2\\x80\\x99", '\u2019')
      .replace("\\xC3\\xA9", '\u00E9')
      .replace("\\xCC\\x81", '\u0301')
  }
}
