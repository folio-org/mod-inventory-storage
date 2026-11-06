package org.folio.services.dereferenceditem;

import static org.folio.rest.tools.utils.TenantTool.tenantId;

import io.vertx.core.Future;
import java.util.Map;
import org.folio.persist.DereferencedItemRepository;
import org.folio.rest.exceptions.NotFoundException;
import org.folio.rest.jaxrs.model.DereferencedItem;
import org.folio.rest.jaxrs.model.DereferencedItems;
import org.springframework.stereotype.Service;

@Service
public class DereferencedItemService {
  private final DereferencedItemRepository repository;

  public DereferencedItemService(DereferencedItemRepository repository) {
    this.repository = repository;
  }

  /**
   * Finds the items matching the CQL, a null query matches all.
   */
  public Future<DereferencedItems> find(String cql, int offset, int limit, Map<String, String> headers) {
    return repository.query(cql, offset, limit, tenantId(headers))
      .map(items -> new DereferencedItems()
        .withDereferencedItems(items)
        .withTotalRecords(items.size()));
  }

  /**
   * Gets the item, fails with {@link NotFoundException} when there is no such item.
   */
  public Future<DereferencedItem> getById(String itemId, Map<String, String> headers) {
    return repository.findDereferencedById(itemId, tenantId(headers))
      .compose(item -> item.map(Future::succeededFuture)
        .orElseGet(() -> Future.failedFuture(
          new NotFoundException("No item records found matching provided UUID."))));
  }
}
