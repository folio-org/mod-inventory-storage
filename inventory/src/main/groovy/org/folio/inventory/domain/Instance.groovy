package org.folio.inventory.domain

class Instance {
  final String id
  final String title
  final List<Map> identifiers

  def Instance(String title, List<Map> identifiers) {
    this(null, title, identifiers)
  }

  def Instance(String title) {
    this(null, title, [])
  }

  def Instance(String id, String title, List<Map> identifiers) {
    this.id = id
    this.title = title
    this.identifiers = identifiers.collect()
  }

  def Instance copyWithNewId(String newId) {
    new Instance(newId, this.title, this.identifiers)
  }

  Instance addIdentifier(Map identifier) {
    new Instance(id, title, this.identifiers.collect() << identifier)
  }

  Instance addIdentifier(String namespace, String value) {
    def identifier = ['namespace' : namespace, 'value' : value]

    new Instance(id, title, this.identifiers.collect() << identifier)
  }

  @Override
  public String toString() {
    println ("Instance ID: ${id}, Title: ${title}")
  }

  Instance removeIdentifier(String namespace, String value) {
    def newIdentifiers = this.identifiers.stream()
      .filter({ !(it.namespace == namespace && it.value == value) })
      .collect()

    new Instance(id, title, newIdentifiers)
  }
}
