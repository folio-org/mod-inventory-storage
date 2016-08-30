package catalogue.core.domain

interface ItemCollection extends Collection<Item>,
        BatchCollection<Item> {
    def findByTitle(String partialTitle, Closure completionCallback)
}