package org.folio.services.authority;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import lombok.SneakyThrows;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.folio.persist.AuthorityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


@ExtendWith(MockitoExtension.class)
class AuthorityServiceTest {

  private static final String FILE = "src/main/resources/files/authorities.csv";
  private static final String EMPTY_FILE = "src/main/resources/files/empty.csv";

  @Mock
  private AuthorityRepository authorityRepository;
  @Mock
  private Context vertxContext;
  @Mock
  private Map<String, String> okapiHeaders;
  @InjectMocks
  private AuthorityService authorityService;

  @BeforeEach
  @SneakyThrows
  public void setUp() {
    FieldUtils.writeField(authorityService, "authorityRepository", authorityRepository, true);
  }

  @Test
  @SneakyThrows
  @SuppressWarnings("unchecked")
  void shouldReadFileAndWriteToDbInBatches() {
    when(authorityRepository.update(any()))
      .thenReturn(Future.succeededFuture(), Future.succeededFuture());

    var result = authorityService.updateAuthorities(FILE, 1).toCompletionStage().toCompletableFuture().get();

    assertNull(result);
    verify(authorityRepository, times(2))
      .update(anyList());
  }

  @Test
  @SneakyThrows
  void shouldFailOnEmptyFileForBatchUpdate() {
    var ex = assertThrows(ExecutionException.class, () -> authorityService.updateAuthorities(EMPTY_FILE, 1)
        .toCompletionStage().toCompletableFuture().get());

    assertEquals(IllegalArgumentException.class, ex.getCause().getClass());
    assertTrue(ex.getMessage().endsWith("File must not be empty."));

    verifyNoInteractions(authorityRepository);
  }

}
