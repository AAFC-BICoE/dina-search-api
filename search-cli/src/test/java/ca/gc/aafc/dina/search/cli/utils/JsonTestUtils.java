package ca.gc.aafc.dina.search.cli.utils;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Utility class to work with json in tests.
 */
public final class JsonTestUtils {

  private JsonTestUtils() {
    //utility class
  }

  public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public static JsonNode readJsonThrows(String jsonAsString) throws JacksonException {
    return OBJECT_MAPPER.readTree(jsonAsString);
  }

  public static JsonNode readJson(String jsonAsString) {
    try {
      return OBJECT_MAPPER.readTree(jsonAsString);
    } catch (JacksonException e) {
      fail(e);
    }
    return null;
  }

}
