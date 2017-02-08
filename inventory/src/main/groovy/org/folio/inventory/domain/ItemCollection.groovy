package org.folio.inventory.domain

interface ItemCollection
  extends AsynchronousCollection<Item>,
    SearchableCollection<Item> {
}
