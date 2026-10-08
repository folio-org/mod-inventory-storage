package org.folio.service.user;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import java.util.Map;
import org.folio.model.User;
import org.folio.service.UserService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Replaces the {@link UserService} of folio-custom-fields, which calls {@code GET /users/{id}} on
 * every custom field create and update only to store the username in the field's metadata. The
 * package is dictated by the library's component scan ({@code org.folio.service}).
 *
 * <p>Remove this class once FCFIELDS-45 (drop the users lookup from the library) is released and
 * the module uses that library version.
 */
@Component
@Primary
public class NoOpUserService extends UserService {

  public NoOpUserService(Vertx vertx) {
    super(vertx);
  }

  @Override
  public Future<User> getUserInfo(Map<String, String> okapiHeaders) {
    return Future.succeededFuture(new User());
  }
}
