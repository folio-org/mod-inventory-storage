package org.folio.inventory.domain

class Item {
  final String id
  final String title
  final String barcode
  final String instanceId

  def Item(String title, String barcode, String instanceId) {
    this(null, title, barcode, instanceId)
  }

  def Item(String id, String title, String barcode, String instanceId) {
    this.id = id
    this.title = title
    this.barcode = barcode
    this.instanceId = instanceId
  }

  def Item copyWithNewId(String id) {
    new Item(id, title, barcode, instanceId)
  }

  @Override
  public String toString() {
    println ("Item ID: ${id}, Title: ${title}, Barcode: ${barcode}")
  }
}
