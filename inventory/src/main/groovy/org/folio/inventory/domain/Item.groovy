package org.folio.inventory.domain

class Item {
  final String id
  final String title
  final String barcode
  final String instanceId
  final String status

  def Item(String id, String title, String barcode,
           String instanceId, String status) {
    this.id = id
    this.title = title
    this.barcode = barcode
    this.instanceId = instanceId
    this.status = status
  }

  def Item(String title, String barcode,
           String instanceId, String status) {
    this(null, title, barcode, instanceId, status)
  }

  def Item copyWithNewId(String newId) {
    new Item(newId, this.title, this.barcode,
      this.instanceId, this.status)
  }

  @Override
  public String toString() {
    println ("Item ID: ${id}, Title: ${title}, Barcode: ${barcode}")
  }
}
