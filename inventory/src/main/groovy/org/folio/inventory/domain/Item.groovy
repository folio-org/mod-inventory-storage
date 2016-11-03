package org.folio.inventory.domain

class Item {
  final String id
  final String title
  final String barcode

  def Item(String title, String barcode) {
    this(null, title, barcode)
  }

  def Item(String id, String title, String barcode) {
    this.id = id
    this.title = title
    this.barcode = barcode
  }

  def Item copyWithNewId(String id) {
    new Item(id, title, barcode)
  }
}
