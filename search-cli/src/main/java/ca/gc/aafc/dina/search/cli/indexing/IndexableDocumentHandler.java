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
import java.util.stream.StreamSupport;

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
      processExternalRelationships(rel, includedArray);
    });
    
    // Only add included section if it has items
    if (!includedArray.isEmpty()) {
      newData.set(JSONApiDocumentStructure.INCLUDED, includedArray);
    }

    // Parse it as json:api document to make it easier
    processReverseRelationships(documentType, jsonApiCompoundDocument.getIdAsStr(), newData);

    // Process augmented relationships - enrich already-included documents with nested relationship references
    processAugmentedRelationships(documentType, newData);

    // Process included section to apply Node transformations if needed (ex: coordinate extraction for geospatial fields)
    applyIncludedNodeTransformation(includedArray);

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
   * @param includeOverride optional override for relationships to include:
   *        - Optional.empty(): use IndexSettingDescriptor defaults (for root document indexing)
   *        - Optional.of(includes): use ApiResourceDescriptor with specified includes (for augmentation/external relationships)
   * @return the parsed JSON document, or empty if the fetch fails
   */
  private Optional<JsonNode> fetchDocument(String type, String id, Optional<Set<String>> includeOverride) {
    try {
      // Get API resource descriptor (required for all fetches)
      ApiResourceDescriptor apiResource = svcEndpointProps.getApiResourceDescriptorForType(type);
      if (apiResource == null) {
        log.warn("No ApiResourceDescriptor found for type={}, cannot fetch document id={}", type, id);
        return Optional.empty();
      }
      
      // Determine includes and optionalFields based on mode
      Set<String> includes;
      Map<String, List<String>> optionalFields;
      
      if (includeOverride.isPresent()) {
        // Custom includes mode: use provided includes (may be empty set)
        includes = includeOverride.get();
        optionalFields = null;
      } else {
        // Normal mode: use IndexSettingDescriptor defaults
        IndexSettingDescriptor indexSettingDescriptor = svcEndpointProps.getIndexSettingDescriptorForType(type);
        if (indexSettingDescriptor == null) {
          log.warn("No IndexSettingDescriptor found for type={}, cannot fetch document id={}", type, id);
          return Optional.empty();
        }
        includes = indexSettingDescriptor.relationships();
        optionalFields = indexSettingDescriptor.optionalFields();
      }
      
      String rawPayload = apiAccess.getFromApi(apiResource, includes, optionalFields, id);
      return Optional.of(OM.readTree(rawPayload));
      
    } catch (SearchApiException | JsonProcessingException ex) {
      log.error("Error fetching document type={}, id={}, message={}", type, id, ex.getMessage());
      return Optional.empty();
    }
  }
  
  /**
   * Processing of the external relationships (objects in other APIs) of a DINA compliant json api object.
   * 
   * Fetches relationship documents and adds them to the included section.
   * 
   * @param relationshipsNode Node containing the relationships section
   * @param includedArray Array containing included json spec objects
   */
  private void processExternalRelationships(JsonNode relationshipsNode, JsonNode includedArray) {
    if (relationshipsNode == null || !relationshipsNode.isObject()) {
      return;
    }

    // Iterate over each relationship
    relationshipsNode.fields().forEachRemaining(relationshipEntry -> {
      JsonNode relationshipData = relationshipEntry.getValue().get(JSONApiDocumentStructure.DATA);
      
      if (relationshipData == null) {
        return;
      }
      
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
        
        if (found) {
          continue;
        }
        
        // Check if we can fetch this type
        if (svcEndpointProps.getApiResourceDescriptorForType(relationshipType) == null) {
          continue;
        }
        
        // Fetch the relationship document
        Optional<JsonNode> fullDocumentOpt = fetchDocument(relationshipType, relationshipId, Optional.of(Set.of()));
        if (fullDocumentOpt.isEmpty()) {
          log.warn("Failed to fetch document: type={}, id={}", relationshipType, relationshipId);
          continue;
        }
        
        JsonNode fullDocument = fullDocumentOpt.get();
        
        // Extract the data section and add to parent's includedArray
        Optional<JsonNode> dataOpt = JsonHelper.atJsonPtr(fullDocument, JSONApiDocumentStructure.DATA_PTR);
        if (dataOpt.isPresent()) {
          ((ArrayNode) includedArray).add(dataOpt.get());
          log.info("Added document to included: type={}, id={}", relationshipType, relationshipId);
        }
      }
    });
  }
  
  /**
   * Process augmented relationships by re-fetching documents already in the included section
   * with additional nested relationship references. Replaces the existing entry with the enriched version
   * that contains relationship type/id references in the relationships section.
   * 
   * @param documentType The type of the parent document
   * @param assembledDoc The assembled document with included section
   */
  private void processAugmentedRelationships(String documentType, JsonNode assembledDoc) {
    IndexSettingDescriptor indexSettings = svcEndpointProps.getIndexSettingDescriptorForType(documentType);
    if (indexSettings == null || CollectionUtils.isEmpty(indexSettings.augmentedRelationships())) {
      return;
    }
    
    JsonNode includedNode = assembledDoc.get(JSONApiDocumentStructure.INCLUDED);
    if (includedNode == null || !includedNode.isArray()) {
      return;
    }
    
    JsonNode relationshipsNode = assembledDoc.at("/data/relationships");
    if (relationshipsNode.isMissingNode() || !relationshipsNode.isObject()) {
      return;
    }
    
    ArrayNode includedArray = (ArrayNode) includedNode;
    
    // For each configured augmented relationship, find and enrich the corresponding documents
    for (AugmentedRelationship augmentedRel : indexSettings.augmentedRelationships()) {
      JsonNode relationship = relationshipsNode.get(augmentedRel.relationshipName());
      if (relationship == null) {
        continue;
      }
      
      List<JsonNode> relationshipRefs = extractRelationshipReferences(relationship);
      for (JsonNode ref : relationshipRefs) {
        augmentDocumentInIncluded(includedArray, ref, augmentedRel.nestedRelationships());
      }
    }
  }
  
  /**
   * Extract type/id references from a relationship node.
   * Handles both single object and array formats.
   * 
   * @param relationshipNode the relationship node containing data
   * @return list of reference objects (with type and id fields)
   */
  private List<JsonNode> extractRelationshipReferences(JsonNode relationshipNode) {
    JsonNode data = relationshipNode.get(JSONApiDocumentStructure.DATA);
    if (data == null || data.isNull()) {
      return List.of();
    }
    
    if (data.isArray()) {
      return StreamSupport.stream(data.spliterator(), false)
          .filter(node -> node.has(JSONApiDocumentStructure.TYPE) && node.has(JSONApiDocumentStructure.ID))
          .toList();
    } else if (data.has(JSONApiDocumentStructure.TYPE) && data.has(JSONApiDocumentStructure.ID)) {
      return List.of(data);
    }
    
    return List.of();
  }
  
  /**
   * Find and augment a document in the included array with nested relationship references.
   * 
   * @param includedArray the included array to search and modify
   * @param reference the reference object containing type and id
   * @param nestedRelationships the nested relationships to include when fetching
   */
  private void augmentDocumentInIncluded(ArrayNode includedArray, JsonNode reference, List<String> nestedRelationships) {
    String type = reference.get(JSONApiDocumentStructure.TYPE).asText();
    String id = reference.get(JSONApiDocumentStructure.ID).asText();
    
    // Find the document in included array
    int index = findDocumentIndex(includedArray, type, id);
    if (index == -1) {
      return; // Not in included array
    }
    
    // Re-fetch with nested includes
    Set<String> includes = Set.copyOf(nestedRelationships);
    log.info("Augmenting document: type={}, id={} with nested includes: {}", type, id, includes);
    
    Optional<JsonNode> enrichedDocOpt = fetchDocument(type, id, Optional.of(includes));
    if (enrichedDocOpt.isEmpty()) {
      log.warn("Failed to fetch augmented document: type={}, id={}", type, id);
      return;
    }
    
    // Extract and replace with enriched data
    Optional<JsonNode> enrichedDataOpt = JsonHelper.atJsonPtr(enrichedDocOpt.get(), JSONApiDocumentStructure.DATA_PTR);
    if (enrichedDataOpt.isPresent()) {
      includedArray.set(index, enrichedDataOpt.get());
    } else {
      log.warn("No data section in enriched document for type={}, id={}", type, id);
    }
  }
  
  /**
   * Find the index of a document in the included array by type and id.
   * 
   * @param includedArray the included array to search
   * @param type the document type
   * @param id the document id
   * @return the index if found, -1 otherwise
   */
  private int findDocumentIndex(ArrayNode includedArray, String type, String id) {
    for (int i = 0; i < includedArray.size(); i++) {
      JsonNode doc = includedArray.get(i);
      if (JsonHelper.safeTextEquals(doc, JSONApiDocumentStructure.TYPE, type)
          && JsonHelper.safeTextEquals(doc, JSONApiDocumentStructure.ID, id)) {
        return i;
      }
    }
    return -1;
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
