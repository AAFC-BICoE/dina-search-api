package ca.gc.aafc.dina.search.cli.indexing;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.function.Function;

/**
 * Gets a node from a JONS:API document and transforms its value.
 */
public class NodeTransformer {

  private static final JsonPointer COORDINATES_PTR = JsonPointer.valueOf("/coordinates");

  public static JsonNode extractCoordinates(JsonNode eventGeomNode) {
    return eventGeomNode.at(COORDINATES_PTR);
  }

  public static void transformNode(JsonNode nodeToTransform, String attribute, Function<JsonNode, JsonNode> transformer) {
    JsonNode node = nodeToTransform.get(attribute);
    if(node != null && !node.isMissingNode()) {
      ((ObjectNode) nodeToTransform).put(attribute, transformer.apply(node));
    }
  }

}
