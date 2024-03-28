package ca.gc.aafc.dina.search.cli.indexing;

import ca.gc.aafc.dina.search.cli.http.CacheableApiAccess;
import ca.gc.aafc.dina.search.cli.json.JSONApiDocumentStructure;
import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.stereotype.Component;

import ca.gc.aafc.dina.search.cli.config.ServiceEndpointProperties;
import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;

import lombok.extern.log4j.Log4j2;

import java.util.Optional;

/**
 * This class handles merging DINA API document with external document references embedded in the
 * passed document. External references are defined within the meta section of the original document.
 *
 * 
 * The assembling process is taking advantage of the included section defined in any JSON API
 * compliant document.
 * 
 * For more information see {@link #assembleDocument(String) assembleDocument} method.
 * 
 */
@Log4j2
@Component
public class IndexableDocumentHandler {

  public static final ObjectMapper OM = new ObjectMapper();

  private final CacheableApiAccess client;
  private final ServiceEndpointProperties svcEndpointProps;

  public IndexableDocumentHandler(CacheableApiAccess client, ServiceEndpointProperties svcEndpointProps) {
    this.client = client;
    this.svcEndpointProps = svcEndpointProps;
  }

  /**
   * This methods assemble or insert externally referenced document within the "included" section
   * of the passed DINA Document.
   * 
   * Any document defined in the "included" with a missing attributes section will be processed for
   * assembling/insertion. If the document type is matching a type supported for assembling, a call 
   * to the service supporting that document will be done to get its attributes. If successfully retrieved
   * the attributes will then be inserted into the document.
   * 
   * Once all the included section is done, some cleanup is done on the "meta" section.
   * 
   * 
   * @param rawPayload payload in raw json (json string)
   * @return document as {@link ObjectNode}
   * @throws SearchApiException
   */
  public ObjectNode assembleDocument(String rawPayload)
      throws SearchApiException, JsonProcessingException {

    JsonNode document = OM.readTree(rawPayload);
    ObjectNode newData = OM.createObjectNode();

    newData.set(JSONApiDocumentStructure.DATA, atJsonPtr(document, JSONApiDocumentStructure.DATA_PTR)
        .orElseThrow(() -> new SearchApiException("JSON:API data section missing")));

    // included section is optional
    atJsonPtr(document, JSONApiDocumentStructure.INCLUDED_PTR).ifPresent(included -> {
      processIncluded(included);
      newData.set(JSONApiDocumentStructure.INCLUDED, included);
    });

    JsonNode metaNode = atJsonPtr(document, JSONApiDocumentStructure.META_PTR)
        .orElseThrow(() -> new SearchApiException("JSON:API meta section missing"));
    processMeta(metaNode);
    newData.set(JSONApiDocumentStructure.META, metaNode);

    return newData;
  }

  private static Optional<JsonNode> atJsonPtr(JsonNode document, JsonPointer ptr) {
    JsonNode node = document.at(ptr);
    return node.isMissingNode() ? Optional.empty() : Optional.of(node);
  }

  /**
   * Processing of the included section of a DINA compliant json api object.
   * 
   * In this current implementation we are resolving entries that are missing
   * their attributes property.
   * 
   * @param includedArray Array containing included json spec objects
   */
  private void processIncluded(JsonNode includedArray) {

    System.out.println("---> processIncluded");

    if (includedArray == null || !includedArray.isArray()) {
      return;
    }

    for (JsonNode curObject : includedArray) {

      if (curObject.get(JSONApiDocumentStructure.ATTRIBUTES) != null || !curObject.isObject()) {
        // Already have the attributes or the node is not an object ... skip the current entry
        continue;
      }

      // Getting the type and perform a level #1 retrieval of attributes
      //
      String type = curObject.get(JSONApiDocumentStructure.TYPE).asText();
      if (svcEndpointProps.getEndpoints().containsKey(type)) {

        // Get the Id and retrieved the attributes from the related object.
        //
        String curObjectId = curObject.get(JSONApiDocumentStructure.ID).asText();

        // Best effort processing for assembling of include section
        try {
          String rawPayload = client.getFromApi(svcEndpointProps.getEndpoints().get(type), curObjectId);

          System.out.println(rawPayload);

          JsonNode document = OM.readTree(rawPayload);
          // Take the data.attributes section to be embedded
          Optional<JsonNode> dataObject = atJsonPtr(document, JSONApiDocumentStructure.ATTRIBUTES_PTR);

          if (dataObject.isPresent()) {
            ((ObjectNode) curObject).set(JSONApiDocumentStructure.ATTRIBUTES, dataObject.get());
          } else {
            // Remove attribute section from the embedded object
            ((ObjectNode) curObject).remove(JSONApiDocumentStructure.ATTRIBUTES);
          }
        } catch (SearchApiException | JsonProcessingException ex) {
          log.error("Error during processing of included section object type{}, id={}, message={}", type, curObjectId, ex.getMessage());
        }
      }
    }
  }

  /**
   * Processing just the meta section of a DINA compliant object. For now we are
   * simply removing the external, we no longer need it in the object to be pushed
   * to elasticsearch.
   * 
   * @param metaObject meta section of the DINA json api object
   */
  private static void processMeta(JsonNode metaObject) {

    if (metaObject == null || !metaObject.isObject()) {
      return;
    }

    // Remove the external entry
    if (metaObject.isObject()) {
      ((ObjectNode) metaObject).remove("external");
    }
  }
}
