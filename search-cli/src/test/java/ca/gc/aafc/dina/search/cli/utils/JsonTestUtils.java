package ca.gc.aafc.dina.search.cli.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Utility class to work with json in tests.
 */
public final class JsonTestUtils {

  private JsonTestUtils() {
    //utility class
  }

  public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public static JsonNode readJsonThrows(String jsonAsString) throws JsonProcessingException {
    return OBJECT_MAPPER.readTree(jsonAsString);
  }

  public static JsonNode readJson(String jsonAsString) {
    try {
      return OBJECT_MAPPER.readTree(jsonAsString);
    } catch (JsonProcessingException e) {
      fail(e);
    }
    return null;
  }

}
