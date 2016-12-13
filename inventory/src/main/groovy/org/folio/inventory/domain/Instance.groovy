package org.folio.inventory.domain

class Instance {
  final String id
  final String title

  def Instance(String title) {
    this(null, title)
  }

  def Instance(String id, String title) {
    this.id = id
    this.title = title
  }

  def Instance copyWithNewId(String newId) {
    new Instance(newId, this.title)
  }

  @Override
  public String toString() {
    println ("Instance ID: ${id}, Title: ${title}")
  }
}
