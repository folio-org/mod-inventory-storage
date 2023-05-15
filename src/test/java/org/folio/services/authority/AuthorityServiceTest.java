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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import lombok.SneakyThrows;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.folio.persist.AuthorityRepository;
import org.folio.rest.jaxrs.model.Authority;
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

  private static final String AUTH_1 = "{\"id\": \"2ab9acdc-6fad-4a3d-93b9-2becf1a2867c\", \"notes\": [], "
    + "\"source\": \"MARC\", \"_version\": 1, \"metadata\": {\"createdDate\": \"2023-04-26T15:08:02.210Z\", "
    + "\"updatedDate\": \"2023-04-26T15:08:02.210Z\", \"createdByUserId\": "
    + "\"04aa0351-bbf8-468e-9a23-629acf54950e\", \"updatedByUserId\": \"04aa0351-bbf8-468e-9a23-629acf54950e\"}, "
    + "\"naturalId\": \"n2008001084\", \"identifiers\": [{\"value\": \"7394284\", \"identifierTypeId\": "
    + "\"5d164f4b-0b15-4e42-ae75-cfcf85318ad9\"}, {\"value\": \"n 2008001084\", \"identifierTypeId\": "
    + "\"c858e4f2-2b6b-4385-842b-60732ee14abb\"}, {\"value\": \"(OCoLC)oca07622362\", \"identifierTypeId\": "
    + "\"7e591197-f335-4afb-bc6d-a6d76ca3bace\"}], \"personalName\": \"Coates, Ta-Nehisi\", \"sftGenreTerm\": [], "
    + "\"sourceFileId\": \"af045f2f-e851-4613-984c-4bc13430454a\", \"saftGenreTerm\": [], \"sftMeetingName\": [], "
    + "\"sftTopicalTerm\": [], \"saftMeetingName\": [], \"saftTopicalTerm\": [], \"sftPersonalName\": [], "
    + "\"sftUniformTitle\": [], \"subjectHeadings\": \"a\", \"saftPersonalName\": [], \"saftUniformTitle\": [], "
    + "\"sftCorporateName\": [], \"saftCorporateName\": [], \"sftGeographicName\": [], \"saftGeographicName\": [], "
    + "\"sftMeetingNameTitle\": [], \"saftMeetingNameTitle\": [], \"sftPersonalNameTitle\": [], "
    + "\"saftPersonalNameTitle\": [], \"sftCorporateNameTitle\": [], \"saftCorporateNameTitle\": []}";
  private static final String AUTH_2 = "{\"id\": \"05bc0aa1-fc6f-408b-9141-8662972b1f2e\", \"notes\": [], "
    + "\"source\": \"MARC\", \"_version\": 1, \"metadata\": {\"createdDate\": \"2023-04-26T15:10:16.377Z\", "
    + "\"updatedDate\": \"2023-04-26T15:10:16.377Z\", \"createdByUserId\": \"04aa0351-bbf8-468e-9a23-629acf54950e\", "
    + "\"updatedByUserId\": \"04aa0351-bbf8-468e-9a23-629acf54950e\"}, \"naturalId\": \"n2008001084\", "
    + "\"identifiers\": [{\"value\": \"7394284\", \"identifierTypeId\": \"5d164f4b-0b15-4e42-ae75-cfcf85318ad9\"}, "
    + "{\"value\": \"n 2008001084\", \"identifierTypeId\": \"c858e4f2-2b6b-4385-842b-60732ee14abb\"}, "
    + "{\"value\": \"(OCoLC)oca07622362\", \"identifierTypeId\": \"7e591197-f335-4afb-bc6d-a6d76ca3bace\"}], "
    + "\"personalName\": \"Coates, Ta-Nehisi\", \"sftGenreTerm\": [], \"sourceFileId\": "
    + "\"af045f2f-e851-4613-984c-4bc13430454a\", \"saftGenreTerm\": [], \"sftMeetingName\": [], "
    + "\"sftTopicalTerm\": [], \"saftMeetingName\": [], \"saftTopicalTerm\": [], \"sftPersonalName\": [], "
    + "\"sftUniformTitle\": [], \"subjectHeadings\": \"a\", \"saftPersonalName\": [], "
    + "\"saftUniformTitle\": [], \"sftCorporateName\": [], \"saftCorporateName\": [], \"sftGeographicName\": [], "
    + "\"saftGeographicName\": [], \"sftMeetingNameTitle\": [], \"saftMeetingNameTitle\": [], "
    + "\"sftPersonalNameTitle\": [], \"saftPersonalNameTitle\": [], \"sftCorporateNameTitle\": [], "
    + "\"saftCorporateNameTitle\": []}";
  private final ObjectMapper objectMapper = new ObjectMapper();

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
    var auths = List.of(objectMapper.readValue(AUTH_1, Authority.class),
      objectMapper.readValue(AUTH_2, Authority.class));
    when(authorityRepository.update(any()))
      .thenReturn(Future.succeededFuture(), Future.succeededFuture());
    when(authorityRepository.getById(any(), any()))
      .thenReturn(Future.succeededFuture(Map.of(auths.get(0).getId(), auths.get(0),
        auths.get(1).getId(), auths.get(1))));

    var result = authorityService.updateAuthorities(FILE, 1).toCompletionStage().toCompletableFuture().get();

    assertNull(result);
    verify(authorityRepository, times(2))
      .update(anyList());
    verify(authorityRepository, times(2))
      .getById(anyList(), any());
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
