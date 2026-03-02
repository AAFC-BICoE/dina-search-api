package ca.gc.aafc.dina.search.cli.indexing;

import ca.gc.aafc.dina.json.JsonHelper;
import ca.gc.aafc.dina.jsonapi.JSONApiDocumentStructure;
import ca.gc.aafc.dina.jsonapi.JsonApiCompoundDocument;
import ca.gc.aafc.dina.search.cli.config.ApiResourceDescriptor;
import ca.gc.aafc.dina.search.cli.config.AugmentedRelationship;
import ca.gc.aafc.dina.search.cli.config.IndexSettingDescriptor;
import ca.gc.aafc.dina.search.cli.config.ReverseRelationship;
import ca.gc.aafc.dina.search.cli.config.ServiceEndpointProperties;
import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;
import ca.gc.aafc.dina.search.cli.exceptions.SearchApiNotFoundException;
import ca.gc.aafc.dina.search.cli.http.DinaApiAccess;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
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

  public static final ObjectMapper OM = new ObjectMapper();

  private static final List<JsonNodeTransformation>
      INCLUDED_NODE_TRANSFORMATION =
      List.of(
          new JsonNodeTransformation(JSONApiDocumentStructure.ATTRIBUTES, "eventGeom",
              JsonNodeTransformer::extractCoordinates));


  private final DinaApiAccess apiAccess;
  private final ServiceEndpointProperties svcEndpointProps;
  private final AtomicBoolean reverseRelationshipErrorReported = new AtomicBoolean(false);

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

    // Get or create the included array
    ArrayNode includedArray = (ArrayNode) JsonHelper.atJsonPtr(document, JSONApiDocumentStructure.INCLUDED_PTR)
        .orElseGet(() -> OM.createArrayNode());

    // Parse document type for augmented relationships lookup
    JsonApiCompoundDocument jsonApiCompoundDocument = OM.readValue(rawPayload, JsonApiCompoundDocument.class);
    String documentType = jsonApiCompoundDocument.getType();

    // relationship is optional - process external relationships and add them to included
    JsonHelper.atJsonPtr(document, JSONApiDocumentStructure.RELATIONSHIP_PTR).ifPresent( rel -> {
      processExternalRelationships(documentType, rel, includedArray);
    });

    // Process included section to apply Node transformations if needed (ex: coordinate extraction for geospatial fields)
    applyIncludedNodeTransformation(includedArray);
    
    // Only add included section if it has items
    if (includedArray.size() > 0) {
      newData.set(JSONApiDocumentStructure.INCLUDED, includedArray);
    }

    // Parse it as json:api document to make it easier
    processReverseRelationships(documentType, jsonApiCompoundDocument.getIdAsStr(), newData);

    JsonNode metaNode = JsonHelper.atJsonPtr(document, JSONApiDocumentStructure.META_PTR)
        .orElseThrow(() -> new SearchApiException("JSON:API meta section missing"));
    processMeta(metaNode);
    newData.set(JSONApiDocumentStructure.META, metaNode);

    return newData;
  }

  /**
   * Fetches a document from the API for a given type and ID.
   * 
   * @param type the document type
   * @param id the document ID
   * @param includeOverride optional override for relationships to include (null = use type's default)
   * @return the parsed JSON document, or empty if the fetch fails
   */
  private Optional<JsonNode> fetchDocument(String type, String id, Set<String> includeOverride) {
    try {
      IndexSettingDescriptor indexSettingDescriptor = svcEndpointProps.getIndexSettingDescriptorForType(type);
      // Use override if provided, otherwise use the type's configured relationships
      Set<String> includes = includeOverride != null ? includeOverride : indexSettingDescriptor.relationships();
      String rawPayload = apiAccess.getFromApi(
          svcEndpointProps.getApiResourceDescriptorForType(type),
          includes,
          indexSettingDescriptor.optionalFields(),
          id);
      return Optional.of(OM.readTree(rawPayload));
    } catch (SearchApiException | JsonProcessingException ex) {
      log.error("Error fetching document type={}, id={}, message={}", type, id, ex.getMessage());
      return Optional.empty();
    }
  }
  
  /**
   * Processing of the external relationships (objects in other APIs) of a DINA compliant json api object.
   * 
   * Fetches relationship documents and adds them to the included section. For augmented relationships,
   * uses the JSON:API include parameter to fetch nested relationships in a single request, then extracts
   * and flattens them. The included section is stripped from all fetched documents to prevent mapping explosion.
   * 
   * @param parentType The type of the parent document being assembled
   * @param relationshipsNode Node containing the relationships section
   * @param includedArray Array containing included json spec objects
   */
  private void processExternalRelationships(String parentType, JsonNode relationshipsNode, JsonNode includedArray) {
    if (relationshipsNode == null || !relationshipsNode.isObject()) {
      return;
    }

    // Get the parent's index settings to check for augmented relationships
    IndexSettingDescriptor parentIndexSettings = svcEndpointProps.getIndexSettingDescriptorForType(parentType);

    // Iterate over each relationship
    relationshipsNode.fields().forEachRemaining(relationshipEntry -> {
      String relationshipName = relationshipEntry.getKey();
      JsonNode relationshipData = relationshipEntry.getValue().get(JSONApiDocumentStructure.DATA);
      
      if (relationshipData == null) {
        return;
      }
      
      // Check if this relationship is configured as augmented
      boolean isAugmented = parentIndexSettings != null 
          && parentIndexSettings.isAugmentedRelationship(relationshipName);
      
      // Handle both single object and array formats
      JsonNode dataArray = relationshipData.isArray() ? relationshipData : OM.createArrayNode().add(relationshipData);
      
      for (JsonNode curObject : dataArray) {
        if (curObject.get(JSONApiDocumentStructure.ID) == null || curObject.get(JSONApiDocumentStructure.TYPE) == null) {
          continue;
        }
        
        String relationshipId = curObject.get(JSONApiDocumentStructure.ID).asText();
        String relationshipType = curObject.get(JSONApiDocumentStructure.TYPE).asText();
        
        // Check if the document is already in the includedArray (by id and type)
        boolean found = false;
        for (JsonNode includedObject : includedArray) {
          if (JsonHelper.safeTextEquals(includedObject, JSONApiDocumentStructure.ID, relationshipId)
            && JsonHelper.safeTextEquals(includedObject, JSONApiDocumentStructure.TYPE, relationshipType)) {
            found = true;
            break;
          }
        }
        
        if (found || !svcEndpointProps.isTypeSupportedForEndpointDescriptor(relationshipType)) {
          continue;
        }
        
        // For augmented relationships, fetch with nested relationships included
        Set<String> includeOverride = null;
        if (isAugmented) {
          Optional<AugmentedRelationship> augmentedRelOpt = parentIndexSettings.getAugmentedRelationship(relationshipName);
          if (augmentedRelOpt.isPresent()) {
            // Convert List to Set for the include parameter
            includeOverride = Set.copyOf(augmentedRelOpt.get().nestedRelationships());
            log.info("Fetching augmented relationship: {} with nested includes: {}", relationshipName, includeOverride);
          }
        }
        
        // Fetch the relationship document (with nested relationships if augmented)
        Optional<JsonNode> fullDocumentOpt = fetchDocument(relationshipType, relationshipId, includeOverride);
        if (fullDocumentOpt.isEmpty()) {
          log.warn("Failed to fetch document: type={}, id={}", relationshipType, relationshipId);
          continue;
        }
        
        JsonNode fullDocument = fullDocumentOpt.get();
        
        // Extract the data section, strip included, and add to parent's includedArray
        Optional<JsonNode> dataOpt = JsonHelper.atJsonPtr(fullDocument, JSONApiDocumentStructure.DATA_PTR);
        if (dataOpt.isEmpty()) {
          continue;
        }
        
        JsonNode data = dataOpt.get();
        JsonNode strippedData = stripIncludedSection(data);
        ((ArrayNode) includedArray).add(strippedData);
        log.info("Added document to included: type={}, id={}{}", relationshipType, relationshipId,
            isAugmented ? " (augmented)" : "");
        
        // For augmented relationships, extract nested documents from the included section
        if (isAugmented) {
          JsonHelper.atJsonPtr(fullDocument, JSONApiDocumentStructure.INCLUDED_PTR).ifPresent(nestedIncluded -> {
            if (nestedIncluded.isArray()) {
              for (JsonNode nestedDoc : nestedIncluded) {
                // Check if already in parent's included array
                String nestedId = nestedDoc.has(JSONApiDocumentStructure.ID) 
                    ? nestedDoc.get(JSONApiDocumentStructure.ID).asText() : null;
                String nestedType = nestedDoc.has(JSONApiDocumentStructure.TYPE)
                    ? nestedDoc.get(JSONApiDocumentStructure.TYPE).asText() : null;
                
                if (nestedId == null || nestedType == null) {
                  continue;
                }
                
                boolean alreadyIncluded = false;
                for (JsonNode existing : includedArray) {
                  if (JsonHelper.safeTextEquals(existing, JSONApiDocumentStructure.ID, nestedId)
                      && JsonHelper.safeTextEquals(existing, JSONApiDocumentStructure.TYPE, nestedType)) {
                    alreadyIncluded = true;
                    break;
                  }
                }
                
                if (!alreadyIncluded) {
                  // Strip included section from nested document before adding
                  JsonNode strippedNested = stripIncludedSection(nestedDoc);
                  ((ArrayNode) includedArray).add(strippedNested);
                  log.info("Added nested document to included: type={}, id={}", nestedType, nestedId);
                }
              }
            }
          });
        }
      }
    });
  }

  /**
   * Strip the included section from a JSON node to prevent mapping explosion.
   * Creates a copy of the node without the included field.
   * 
   * @param node the node to strip
   * @return a copy of the node without the included section
   */
  private JsonNode stripIncludedSection(JsonNode node) {
    if (!node.isObject()) {
      return node;
    }
    
    ObjectNode copy = ((ObjectNode) node).deepCopy();
    copy.remove(JSONApiDocumentStructure.INCLUDED);
    return copy;
  }

  /**
   * Apply transformations to nodes within the included section of a DINA compliant json api object.

   * 
   * Currently applies coordinate extraction transformations to specific attributes
   * as defined in INCLUDED_NODE_TRANSFORMATION.
   * 
   * @param includedArray Array containing included json spec objects
   */
  private void applyIncludedNodeTransformation (JsonNode includedArray) {

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
        if (apiRd != null && apiRd.isEnabled(true)) {
          try {
            log.debug("Checking for reverse relationship type:{}, relationshipName:{}, id: {}", apiRd.type(),rr.relationshipName(), documentId);
            String rawPayload = apiAccess.getFromApiByFilter(apiRd, null, Map.of(), Pair.of("filter[" + rr.relationshipName() + "]", documentId));

            // this is expected to be an array
            JsonNode document = OM.readTree(rawPayload);
            if (JsonHelper.hasFieldAndIsArray(document, JSONApiDocumentStructure.DATA)) {
              JsonNode dataArray = document.get(JSONApiDocumentStructure.DATA);
              for (JsonNode dataItem : dataArray) {
                // Check if included section exists within the current document
                if (newDoc.has(JSONApiDocumentStructure.INCLUDED)) {
                  log.debug("Included section exists already, adding the following data item: ");
                  log.debug(dataItem.toString());
                  JsonNode included = newDoc.get(JSONApiDocumentStructure.INCLUDED);
                  if (included.isArray()) {
                    ((ArrayNode) included).add(dataItem);
                  } else {
                    log.error("Error processing reverse relationships : Included section is not an array");
                  }
                } else {
                  // Create the included section if it does not exist.
                  log.debug("Create the included section, adding the following data item: ");
                  log.debug(dataItem.toString());
                  ArrayNode included = OM.createArrayNode();
                  included.add(dataItem);
                  ((ObjectNode) newDoc).set(JSONApiDocumentStructure.INCLUDED, included);
                }
              }
            }
          } catch (SearchApiNotFoundException ex) {
            // no-op
            log.debug("No reverse relationship found for type:{}, relationshipName:{}, id: {}", apiRd.type(),rr.relationshipName(), documentId);
          } catch (SearchApiException ex) {
            if (reverseRelationshipErrorReported.compareAndSet(false, true)) {
              log.error("Exception processing reverse relationships. This won't be reported again.", ex);
            }
          } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
          }
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
