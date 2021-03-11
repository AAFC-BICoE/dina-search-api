package ca.gc.aafc.dina.search.cli.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.ReadContext;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;

import org.springframework.stereotype.Component;

import ca.gc.aafc.dina.search.cli.config.ServiceEndpointProperties;
import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;
import ca.gc.aafc.dina.search.cli.http.OpenIDHttpClient;
import lombok.extern.log4j.Log4j2;

/**
 * This class handle merging DINA API document with external document references embedded in the 
 * passed document. Extrernal references are defined within the meta section of the orginal document.
 * 
 * In this current version of the code, assemble is supported for the following document types:
 *  - Metadata
 *  - Organization
 *  - Person
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

  public static final Configuration JACKSON_JSON_NODE_CONFIGURATION = 
      Configuration.builder()
        .mappingProvider(new JacksonMappingProvider())
        .jsonProvider(new JacksonJsonNodeJsonProvider())
        .build();

  public static final String JSON_PATH_DATA = "$.data";
  public static final String JSON_PATH_DATA_ATTRIBUTES = "$.data.attributes";
  public static final String JSON_PATH_DATA_RELATIONSHIPS = "$.data.relationships";

  public static final String JSON_PATH_INCLUDED = "$.included";
  public static final String JSON_PATH_META = "$.meta";

  private static final String PUBLISH_DATA = "data";
  private static final String PUBLISH_INCLUDED = "included";
  private static final String PUBLISH_META = "meta";

  private static final ObjectMapper OM = new ObjectMapper();

  private final OpenIDHttpClient aClient;
  private final ServiceEndpointProperties svcEndpointProps;

  public IndexableDocumentHandler(OpenIDHttpClient aClient, ServiceEndpointProperties svcEndpointProps) {
    this.aClient = aClient;
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
   * @param rawPayload
   * @return document as json string
   * @throws SearchApiException
   */
  public String assembleDocument(String rawPayload)
      throws SearchApiException {

    JsonNode dataObject = parseJsonRaw(rawPayload, JSON_PATH_DATA);

    JsonNode includedArray = parseJsonRaw(rawPayload, JSON_PATH_INCLUDED);
    if (includedArray != null) {
      processIncluded(includedArray);
    }

    JsonNode metaObject = parseJsonRaw(rawPayload, JSON_PATH_META);
    if (metaObject != null) {
      processMeta(metaObject);
    }

    ObjectNode newData = OM.createObjectNode();

    newData.set(PUBLISH_DATA, dataObject);

    if (includedArray != null) {
      newData.set(PUBLISH_INCLUDED, includedArray);
    }

    if (metaObject != null) {
      newData.set(PUBLISH_META, metaObject);
    }

    return newData.toString();
  }

  /**
   * Parse json raw string into a {@link JsonNode} from the supplied jsonPath.
   * @param jsonRawPayload
   * @param jsonPath
   * @return
   */
  private JsonNode parseJsonRaw(String jsonRawPayload, String jsonPath) {

    ReadContext documentCtx = JsonPath.using(JACKSON_JSON_NODE_CONFIGURATION).parse(jsonRawPayload);

    JsonNode jsonNode = null;
    try {
      jsonNode = documentCtx.read(jsonPath);
    } catch (PathNotFoundException pPathNotFoundEx) {
      // This is not really an error, but an indication that the element is not present.
      log.warn("Element {} not found,", jsonPath, pPathNotFoundEx);
    }
    return jsonNode;
  }


  /**
   * Processing of the included section of a DINA compliant json api object.
   * 
   * In this current implementation we are resolving entries that are missing
   * their attributes property.
   * 
   * @param includedArray Array containing included json spec objects
   * 
   * @throws SearchApiException
   */
  private void processIncluded(JsonNode includedArray)
      throws SearchApiException {

    if (includedArray == null || !includedArray.isArray()) {
      return;
    }

    // Start processing each entry of the included data
    //
    for (JsonNode curObject : includedArray) {

      if (curObject.get("attributes") != null) {
        // Already have the attributes...just skip the current entry
        continue;
      }

      // Getting the type and perform a level #1 retrieval of attributes
      //
      String type = curObject.get("type").asText();
      if (svcEndpointProps.getEndpoints().containsKey(type)) {

        // Get the Id and retrieved the attributes from the related object.
        //
        String curObjectId = curObject.get("id").asText();
        String rawPayload = null;

        // Best effort processing for assembling of include section
        try {
          
          rawPayload = aClient.getDataFromUrl(svcEndpointProps.getEndpoints().get(type), curObjectId);

          // Take the data.attributes section to be embedded....
          //
          JsonNode dataObject = parseJsonRaw(rawPayload, JSON_PATH_DATA_ATTRIBUTES);

          // At this stage we have the type, id and attributes for the object
          //
          // First pass, we can embed the object right away...
          //
          if (curObject.isObject()) {
            ((ObjectNode) curObject).set("attributes", dataObject);
          }
        } catch (SearchApiException apiEx) {
          log.error("Error during processing of included section");
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
  private void processMeta(JsonNode metaObject) {

    if (metaObject == null || !metaObject.isObject()) {
      return;
    }

    // Remove the external entry
    if (metaObject.isObject()) {
      ((ObjectNode) metaObject).remove("external");
    }
  }
}
