package org.folio.inventory.domain

class Item {
  final String id
  final String title
  final String barcode
  final String instanceId
  final String status
  final Map materialType
  final String location

  def Item(String id, String title, String barcode,
           String instanceId, String status, Map materialType,
           String location) {
    this.id = id
    this.title = title
    this.barcode = barcode
    this.instanceId = instanceId
    this.status = status
    this.materialType = materialType
    this.location = location
  }

  def Item(String title, String barcode,
           String instanceId, String status, Map materialType,
           String location) {
    this(null, title, barcode, instanceId, status, materialType, location)
  }

  def Item copyWithNewId(String newId) {
    new Item(newId, this.title, this.barcode,
      this.instanceId, this.status, this.materialType, this.location)
  }

  def changeStatus(String newStatus) {
    new Item(this.id, this.title, this.barcode,
      this.instanceId, newStatus, this.materialType, this.location)
  }

  @Override
  public String toString() {
    println ("Item ID: ${id}, Title: ${title}, Barcode: ${barcode}")
  }

}
