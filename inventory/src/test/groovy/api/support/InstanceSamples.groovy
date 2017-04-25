package api.support

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject

class InstanceSamples {
  static JsonObject createInstanceRequest(
    UUID id, String title, JsonArray identifiers) {

    new JsonObject()
      .put("id",id.toString())
      .put("title", title)
      .put("identifiers", identifiers)
  }

  static JsonObject smallAngryPlanet(UUID id) {
    def identifiers = new JsonArray()

    identifiers.add(identifier("isbn", "9781473619777"))

    return createInstanceRequest(id, "Long Way to a Small Angry Planet",
      identifiers)
  }

  static JsonObject nod(UUID id) {
    def identifiers = new JsonArray()

    identifiers.add(identifier("asin", "B01D1PLMDO"))

    createInstanceRequest(id, "Nod", identifiers)
  }

  static JsonObject uprooted(UUID id) {

    def identifiers = new JsonArray();

    identifiers.add(identifier("isbn", "1447294149"));
    identifiers.add(identifier("isbn", "9781447294146"));

    createInstanceRequest(id, "Uprooted",
      identifiers);
  }

  static JsonObject temeraire(UUID id) {

    def identifiers = new JsonArray();

    identifiers.add(identifier("isbn", "0007258712"));
    identifiers.add(identifier("isbn", "9780007258710"));

    createInstanceRequest(id, "Temeraire",
      identifiers);
  }

  static JsonObject leviathanWakes(UUID id) {
    def identifiers = new JsonArray()

    identifiers.add(identifier("isbn", "1841499897"))
    identifiers.add(identifier("isbn", "9781841499895"))

    createInstanceRequest(id, "Leviathan Wakes", identifiers)
  }

  static JsonObject taoOfPooh(UUID id) {
    def identifiers = new JsonArray()

    identifiers.add(identifier("isbn", "1405204265"))
    identifiers.add(identifier("isbn", "9781405204265"))

    createInstanceRequest(id, "Tao of Pooh", identifiers)
  }

  private static JsonObject identifier(String namespace, String value) {
    return new JsonObject()
      .put("namespace", namespace)
      .put("value", value);
  }

  static JsonObject girlOnTheTrain(UUID id) {
    def identifiers = new JsonArray()

    identifiers.add(identifier("asin", "B01LO7PJOE"))

    return createInstanceRequest(id, "The Girl on the Train",
      identifiers)
  }

}
