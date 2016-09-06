package knowledgebase.core.domain

interface InstanceCollection extends ItemCollection<Instance>,
  BatchItemCollection<Instance> {
  List<Instance> findByPartialTitle(String partialName)

  List<Instance> findByIdentifier(String namespace, String identifier)

  void findByPartialTitle(String partialName, Closure resultCallback)

  void findByIdentifier(String namespace, String identifier, Closure resultCallback)
}