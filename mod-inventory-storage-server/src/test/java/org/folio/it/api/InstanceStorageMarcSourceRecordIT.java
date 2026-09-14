package org.folio.it.api;

import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.apache.http.HttpStatus.SC_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;
import static org.folio.support.ResourcePaths.INSTANCES;

import io.vertx.core.json.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.apache.commons.io.IOUtils;
import org.folio.rest.jaxrs.model.MarcJson;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InstanceStorageMarcSourceRecordIT extends InstanceStorageTestBase {

  @Test
  @DisplayName("should create an instance MARC source record")
  void shouldCreateInstanceMarcSourceRecord() {
    var id = UUID.randomUUID();
    createInstance(smallAngryPlanet(id));

    putMarcJson(id, marcJson);

    var getResponse = getMarcJson(id);
    assertThat(getResponse.status()).isEqualTo(SC_OK);
    var body = getResponse.jsonBody();
    assertThat(body.getString("id")).isEqualTo(id.toString());
    assertThat(body.getString("leader")).isEqualTo("xxxxxnam a22yyyyy c 4500");
    var fields = body.getJsonArray("fields");
    assertThat(fields.getJsonObject(0).getString("001")).isEqualTo("029857716");
    assertThat(fields.getJsonObject(1).getJsonObject("245").getJsonArray("subfields").getJsonObject(0)
      .getString("a")).isEqualTo("The Yearbook of Okapiology");
    assertThat(body.getMap().keySet()).containsExactlyInAnyOrder("id", "leader", "fields");
    assertThat(body.getString(STATUS_UPDATED_DATE_PROPERTY)).isNull();
  }

  @Test
  @DisplayName("should update an instance MARC source record")
  void shouldUpdateInstanceMarcSourceRecord() throws IOException {
    var id = UUID.randomUUID();
    createInstance(smallAngryPlanet(id));

    putMarcJson(id, marcJson);
    putMarcJson(id, toMarcJson("/101073931X.mrcjson"));

    var getResponse = getMarcJson(id);
    assertThat(getResponse.status()).isEqualTo(SC_OK);
    assertThat(getResponse.jsonBody().getJsonArray("fields").getJsonObject(0).getString("001"))
      .isEqualTo("101073931X");
  }

  @Test
  @DisplayName("should return 404 when getting a MARC source record that does not exist")
  void shouldReturn404_whenGettingNonExistingSourceRecord() {
    assertMarcJsonNotFound(UUID.randomUUID());
  }

  @Test
  @DisplayName("should delete an instance's MARC source record")
  void shouldDeleteInstanceMarcSourceRecord() {
    var id = UUID.randomUUID();
    createInstance(smallAngryPlanet(id));
    assertThat(getSourceRecordFormat(id)).isNull();

    putMarcJson(id, marcJson);
    assertThat(getSourceRecordFormat(id)).isEqualTo("MARC-JSON");

    var deleteResponse =
      await(doDelete(client, INSTANCES + "/" + id + "/source-record/marc-json"));
    assertThat(deleteResponse.status()).isEqualTo(SC_NO_CONTENT);
    assertThat(getSourceRecordFormat(id)).isNull();
    assertMarcJsonNotFound(id);
  }

  @Test
  @DisplayName("should delete an instance's source record")
  void shouldDeleteInstanceSourceRecord() {
    var id = UUID.randomUUID();
    createInstance(smallAngryPlanet(id));
    assertThat(getSourceRecordFormat(id)).isNull();

    putMarcJson(id, marcJson);
    assertThat(getSourceRecordFormat(id)).isEqualTo("MARC-JSON");

    var deleteResponse = await(doDelete(client, INSTANCES + "/" + id + "/source-record"));
    assertThat(deleteResponse.status()).isEqualTo(SC_NO_CONTENT);
    assertThat(getSourceRecordFormat(id)).isNull();
    assertMarcJsonNotFound(id);
  }

  @Test
  @DisplayName("should delete the source record when deleting the instance")
  void shouldDeleteSourceRecord_whenDeletingInstance() {
    var id = UUID.randomUUID();
    createInstance(smallAngryPlanet(id));
    putMarcJson(id, marcJson);

    var deleteResponse = await(doDelete(client, INSTANCES + "/" + id));
    assertThat(deleteResponse.status()).isEqualTo(SC_NO_CONTENT);

    assertMarcJsonNotFound(id);
  }

  @Test
  @DisplayName("should return 404 when creating a source record without a matching instance")
  void shouldReturn404_whenCreatingSourceRecordWithoutInstance() {
    var id = UUID.randomUUID();

    var response = await(doPut(client, INSTANCES + "/" + id + "/source-record/marc-json",
      JsonObject.mapFrom(marcJson)));

    assertThat(response.status()).isEqualTo(SC_NOT_FOUND);
  }

  private static TestResponse getMarcJson(UUID id) {
    return await(doGet(client, INSTANCES + "/" + id + "/source-record/marc-json"));
  }

  private static String getSourceRecordFormat(UUID id) {
    return getById(id).jsonBody().getString("sourceRecordFormat");
  }

  private static MarcJson toMarcJson(String resourcePath) throws IOException {
    var mrcjson = IOUtils.toString(
      Objects.requireNonNull(InstanceStorageMarcSourceRecordIT.class.getResourceAsStream(resourcePath)),
      StandardCharsets.UTF_8);
    var json = new JsonObject(mrcjson);
    List<Object> fields = new ArrayList<>(json.getJsonArray("fields").getList());
    return new MarcJson().withLeader(json.getString("leader")).withFields(fields);
  }
}
