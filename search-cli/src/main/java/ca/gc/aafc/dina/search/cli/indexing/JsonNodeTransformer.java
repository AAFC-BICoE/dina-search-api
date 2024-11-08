package ca.gc.aafc.dina.search.cli.indexing;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.log4j.Log4j2;

import java.util.function.Function;

/**
 * Gets a node from a JONS:API document and transforms its value.
 */
@Log4j2
public class JsonNodeTransformer {

  private static final JsonPointer COORDINATES_PTR = JsonPointer.valueOf("/coordinates");

  /**
   * Extracts the JsonNode under the property "coordinates".
   * @param eventGeomNode
   * @return
   */
  public static JsonNode extractCoordinates(JsonNode eventGeomNode) {
    return eventGeomNode.at(COORDINATES_PTR);
  }

  /**
   * From a node, get the JsonNode from an attribute and apply the transformer to it.
   * Replace its value with the result of the transformation.
   *
   * @param nodeToTransform
   * @param attribute
   * @param transformer
   */
  public static void transformNode(JsonNode nodeToTransform, String attribute, Function<JsonNode, JsonNode> transformer) {
    JsonNode node = nodeToTransform.get(attribute);
    if (node != null && !node.isMissingNode()) {
      if (nodeToTransform instanceof ObjectNode objectNode) {
        objectNode.replace(attribute, transformer.apply(node));
      } else {
        log.debug("Not and instance of ObjectNode");
      }
    }
  }
}
