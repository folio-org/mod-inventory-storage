package catalogue.core.domain

interface BatchCollection<T> {
    List<T> add(List<T> items)
}
