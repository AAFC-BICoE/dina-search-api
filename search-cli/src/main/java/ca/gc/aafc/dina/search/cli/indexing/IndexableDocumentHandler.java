package ca.gc.aafc.dina.search.cli.indexing;

import ca.gc.aafc.dina.json.JsonHelper;
import ca.gc.aafc.dina.jsonapi.JSONApiDocumentStructure;
import ca.gc.aafc.dina.jsonapi.JsonApiDocument;
import ca.gc.aafc.dina.search.cli.config.ApiResourceDescriptor;
import ca.gc.aafc.dina.search.cli.config.IndexSettingDescriptor;
import ca.gc.aafc.dina.search.cli.config.ReverseRelationship;
import ca.gc.aafc.dina.search.cli.config.ServiceEndpointProperties;
import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;
import ca.gc.aafc.dina.search.cli.exceptions.SearchApiNotFoundException;
import ca.gc.aafc.dina.search.cli.http.DinaApiAccess;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

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

  public static final ObjectMapper OM = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private static final List<JsonNodeTransformation>
      INCLUDED_NODE_TRANSFORMATION =
      List.of(
          new JsonNodeTransformation(JSONApiDocumentStructure.ATTRIBUTES, "eventGeom",
              JsonNodeTransformer::extractCoordinates));


  private final DinaApiAccess apiAccess;
  private final ServiceEndpointProperties svcEndpointProps;

  public IndexableDocumentHandler(DinaApiAccess apiAccess, ServiceEndpointProperties svcEndpointProps) {
    this.apiAccess = apiAccess;
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
    
    newData.set(JSONApiDocumentStructure.DATA, JsonHelper.atJsonPtr(document, JSONApiDocumentStructure.DATA_PTR)
        .orElseThrow(() -> new SearchApiException("JSON:API data section missing")));

    // included section is optional
    JsonHelper.atJsonPtr(document, JSONApiDocumentStructure.INCLUDED_PTR).ifPresent(included -> {
      processIncluded(included);
      newData.set(JSONApiDocumentStructure.INCLUDED, included);
    });

    // Parse it as json:api document to make it easier
    JsonApiDocument jsonApiDocument = OM.readValue(rawPayload, JsonApiDocument.class);
    processReverseRelationships(jsonApiDocument.getData().getType(), jsonApiDocument.getId().toString(), newData);

    JsonNode metaNode = JsonHelper.atJsonPtr(document, JSONApiDocumentStructure.META_PTR)
        .orElseThrow(() -> new SearchApiException("JSON:API meta section missing"));
    processMeta(metaNode);
    newData.set(JSONApiDocumentStructure.META, metaNode);

    return newData;
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

    if (includedArray == null || !includedArray.isArray()) {
      return;
    }
    
    for (JsonNode curObject : includedArray) {
      //Check for potential node transformations
      for (JsonNodeTransformation jst : INCLUDED_NODE_TRANSFORMATION) {
        if (curObject.has(jst.nodeName())) {
          JsonNodeTransformer.transformNode(curObject.get(jst.nodeName()), jst.attribute(), jst.transformer());
        }
      }

      if (curObject.get(JSONApiDocumentStructure.ATTRIBUTES) != null || !curObject.isObject()) {
        // Already have the attributes or the node is not an object ... skip the current entry
        continue;
      }

      // Getting the type and perform a level #1 retrieval of attributes
      //
      String type = curObject.get(JSONApiDocumentStructure.TYPE).asText();
      if (svcEndpointProps.isTypeSupportedForEndpointDescriptor(type)) {

        // Get the Id and retrieved the attributes from the related object.
        //
        String curObjectId = curObject.get(JSONApiDocumentStructure.ID).asText();

        // Best effort processing for assembling of include section
        try {
          String rawPayload = apiAccess.getFromApi(svcEndpointProps.getApiResourceDescriptorForType(type),
              svcEndpointProps.getIndexSettingDescriptorForType(type).relationships(), curObjectId);
          JsonNode document = OM.readTree(rawPayload);
          // Take the data.attributes section to be embedded
          Optional<JsonNode> dataObject = JsonHelper.atJsonPtr(document, JSONApiDocumentStructure.ATTRIBUTES_PTR);

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
   * Check if the provided type has a possible reverse relationship. If yes, try to get it.
   * @param documentType
   * @param documentId
   * @throws SearchApiException
   */
  public void processReverseRelationships(String documentType, String documentId, JsonNode newDoc) throws SearchApiException {
    IndexSettingDescriptor indexSettingDescriptor = svcEndpointProps.getIndexSettingDescriptorForType(documentType);

    if (indexSettingDescriptor != null && CollectionUtils.isNotEmpty(indexSettingDescriptor.reverseRelationships())) {
      for (ReverseRelationship rr : indexSettingDescriptor.reverseRelationships()) {
        ApiResourceDescriptor apiRd = svcEndpointProps.getApiResourceDescriptorForType(rr.type());
        try {
          String rawPayload = apiAccess.getFromApiByFilter(apiRd, null, Pair.of("filter[" + rr.relationshipName() + "]", documentId));

          // this is expected to be an array
          JsonNode document = OM.readTree(rawPayload);

          if (document.has(JSONApiDocumentStructure.DATA)) {
            JsonNode dataArray = document.get(JSONApiDocumentStructure.DATA);
            if (dataArray.isArray()) {
              for (JsonNode dataItem : dataArray) {
                // Check if included section exists within the current document
                if (newDoc.has(JSONApiDocumentStructure.INCLUDED)) {
                  JsonNode included = newDoc.get(JSONApiDocumentStructure.INCLUDED);
                  if (included.isArray()) {
                    ((ArrayNode) included).add(dataItem);
                  } else {
                    log.error("Error processing reverse relationships : Included section is not an array");
                  }
                } else {
                  // Create the included section if it does not exist.
                  ArrayNode included = OM.createArrayNode();
                  included.add(dataItem);
                  ((ObjectNode) newDoc).set(JSONApiDocumentStructure.INCLUDED, included);
                }
              }
            }
          }
        } catch (SearchApiNotFoundException ex) {
          // no-op,
        } catch (JsonProcessingException e) {
          throw new RuntimeException(e);
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

  record JsonNodeTransformation(String nodeName, String attribute, Function<JsonNode, JsonNode> transformer) {
  }
}
