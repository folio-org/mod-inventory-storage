package knowledgebase.core.domain

class Instance {

    final String id
    final String title
    final List<Map> identifiers

    def Instance(String id, String title, List<Map> identifiers) {
        this.id = id
        this.title = title
        this.identifiers = identifiers.collect()
    }

    def Instance(String title, List<Map> identifiers) {
        this(null, title, identifiers)
    }

    def Instance(String title) {
        this(null, title, [])
    }

    Instance addIdentifier(Map identifier)
    {
        new Instance(id, title, this.identifiers.collect() << identifier)
    }

    Instance addIdentifier(String namespace, String value)
    {
        addIdentifier([namespace: namespace, value: value])
    }

    Map toMap() {
        def instanceMap = [:]
        instanceMap.id = this.id
        instanceMap.title = this.title
        instanceMap.identifiers = this.identifiers.collect()
        instanceMap
    }

    @Override
    def String toString() {
        String.format("Id: %s, Title: %s", this.id, this.title)
    }
}
