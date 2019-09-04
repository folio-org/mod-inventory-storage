package schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.json.JsonObject;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

public class InstanceSchemaTest {

  private static final String EXPECTED_INSTANCE_SCHEMA_PATH = "ramls/instance.json";
  private static final String ACTUAL_INSTANCE_SCHEMA_PATH = "schema/instance.json";
  private static final String EXPECTED_SCHEMAS_PARENT_PATH = "ramls/";
  private static final String ACTUAL_SCHEMAS_PARENT_PATH = "schema/";

  /**
   * This test is a kind of notification that the instance schema has been changed.
   * Changing the instance schema breaks file processing workflow (data-import), to avoid file processing failure
   * should update instance schema in mod-source-record-manager module.
   * Please, update the instance schema in mod-source-record-manager repo
   * by link https://github.com/folio-org/mod-source-record-manager/blob/master/ramls/instance.json.
   * To fix this test one should update instance schema and update/add other related schemas to 'schema' directory in tests resources.
   */
  @Test
  public void instanceSchemaIsNotChanged() throws IOException {
    assertSchemasEquals(EXPECTED_INSTANCE_SCHEMA_PATH, ACTUAL_INSTANCE_SCHEMA_PATH);

    ObjectMapper mapper = new ObjectMapper();
    JsonNode instanceSchemaAsJson = mapper.readTree(new File(EXPECTED_INSTANCE_SCHEMA_PATH));
    ArrayList<String> referencesList = new ArrayList<>();
    getReferencesToExternalSchemas(instanceSchemaAsJson, referencesList);

    for (String referenceToSchema : referencesList) {
      String expectedSchemaPath = EXPECTED_SCHEMAS_PARENT_PATH + referenceToSchema;
      String actualSchemaPath = ACTUAL_SCHEMAS_PARENT_PATH + referenceToSchema;
      assertSchemasEquals(expectedSchemaPath, actualSchemaPath);
    }
  }

  private void assertSchemasEquals(String expectedSchemaPath, String actualSchemaPath) throws IOException {
    String expectedSchemaContent = FileUtils.readFileToString(new File(expectedSchemaPath));

    URL expectedInstanceSchemaUrl = InstanceSchemaTest.class.getClassLoader().getResource(actualSchemaPath);
    Assert.assertNotNull( String.format("Schema file '%s' not found in test resources", actualSchemaPath), expectedInstanceSchemaUrl);
    String actualSchemaContent = FileUtils.readFileToString(new File(expectedInstanceSchemaUrl.getFile()));

    JsonObject expectedSchemaAsJson = new JsonObject(expectedSchemaContent);
    JsonObject actualSchemaAsJson = new JsonObject(actualSchemaContent);
    Assert.assertEquals(expectedSchemaAsJson, actualSchemaAsJson);
  }

  private void getReferencesToExternalSchemas(JsonNode jsonNode, List<String> referencesList) throws IOException {
    Iterator<Entry<String, JsonNode>> fieldsIterator = jsonNode.fields();

    while (fieldsIterator.hasNext()) {
      Entry<String, JsonNode> field = fieldsIterator.next();
      if (field.getKey().equals("$ref")) {
        referencesList.add(field.getValue().asText());
        JsonNode jsonNodeByReference = getJsonNodeByReference(EXPECTED_SCHEMAS_PARENT_PATH + field.getValue().asText());
        getReferencesToExternalSchemas(jsonNodeByReference, referencesList);
      } else {
        JsonNode fieldValue = field.getValue();
        if (fieldValue.isObject()) {
          getReferencesToExternalSchemas(fieldValue, referencesList);
        }
      }
    }
  }

  private JsonNode getJsonNodeByReference(String reference) throws IOException {
    return new ObjectMapper().readTree(new File(reference));
  }

}
