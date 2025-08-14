package ca.gc.aafc.dina.search.cli.indexing;

import ca.gc.aafc.dina.jsonapi.JSONApiDocumentStructure;
import ca.gc.aafc.dina.search.cli.config.ApiResourceDescriptor;
import ca.gc.aafc.dina.search.cli.config.IndexSettingDescriptor;
import ca.gc.aafc.dina.search.cli.config.ServiceEndpointProperties;
import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;
import ca.gc.aafc.dina.search.cli.http.OpenIDHttpClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


/**
 * DocumentManager is responsible for:
 * - retrieving the document
 * - coordinating the assemblage
 * - sending the document for indexing based on its type.
 */
@Log4j2
@Service
public class DocumentManager {

  private final OpenIDHttpClient aClient;
  private final ServiceEndpointProperties svcEndpointProps;
  private final IndexableDocumentHandler indexableDocumentHandler;
  private final ElasticSearchDocumentIndexer indexer;
  private final List<String> indexList;

  public DocumentManager(OpenIDHttpClient aClient, ServiceEndpointProperties svcEndpointProps,
                         IndexableDocumentHandler indexableDocumentHandler, ElasticSearchDocumentIndexer indexer) {
    this.aClient = aClient;
    this.svcEndpointProps = svcEndpointProps;
    this.indexableDocumentHandler = indexableDocumentHandler;
    this.indexer = indexer;

    indexList = new ArrayList<>();
    svcEndpointProps.getFilteredEndpointDescriptorStream(ed -> StringUtils.isNotBlank(ed.indexName()))
        .forEach(desc -> indexList.add(desc.indexName()));
  }

  /**
   * Index or re-index the document identified by the type and documentId.
   * @param type the type of document (json:api type)
   * @param documentId the identifier of the document
   * @return the assembled document or null if ignoreUnknown and the type/index is unknown
   */
  public JsonNode indexDocument(String type, String documentId) throws SearchApiException {

    // Validate the type
    if (!isTypeConfigured(type)) {
      throw new SearchApiException("Unsupported endpoint type: " + type);
    }

    IndexSettingDescriptor endpointDescriptor = svcEndpointProps.getIndexSettingDescriptorForType(type);
    ApiResourceDescriptor apiResourceDescriptor = svcEndpointProps.getApiResourceDescriptorForType(type);

    // Step #1: get the document
    log.info("Retrieving document id:{}", documentId);

    String documentToIndex = aClient.getDataById(apiResourceDescriptor, endpointDescriptor.relationships(), documentId);

    // Step #2: Assemble the document into a JSON map
    log.info("Assembling document id:{}", documentId);
    JsonNode jsonNode;
    try {
      jsonNode = indexableDocumentHandler.assembleDocument(documentToIndex);
    } catch (JsonProcessingException ex) {
      throw new SearchApiException("Unable to parse type '" + type + "' with the id '" + documentId + "'", ex);
    }

    // Step #3: Indexing the document into elasticsearch
    log.info("Sending document id:{} to specific index {}", documentId, endpointDescriptor.indexName());
    indexer.indexDocument(documentId, jsonNode, endpointDescriptor.indexName());

    return jsonNode;
  }

  public String deleteDocument(String type, String documentId) throws SearchApiException {

    String processedMessage = null;
    if (!svcEndpointProps.isTypeSupportedForEndpointDescriptor(type)) {
      processedMessage = "Unsupported endpoint type:" + type;
      log.error(processedMessage);
      return processedMessage;
    }

    IndexSettingDescriptor endpointDescriptor = svcEndpointProps.getIndexSettingDescriptorForType(type);

    // Step #2: Delete the document from elasticsearch
    if (StringUtils.isNotBlank(endpointDescriptor.indexName())) {
      log.info("Deleting document id:{} from specific index {}", documentId, endpointDescriptor.indexName());
      indexer.deleteDocument(documentId, endpointDescriptor.indexName());
    }
    
    return processedMessage;
  }

  /**
   * Same as {@link #processEmbeddedDocument(List, String, String)} but for all indices.
   * @param documentType
   * @param documentId
   * @throws SearchApiException
   */
  public void processEmbeddedDocument(String documentType, String documentId) throws SearchApiException {
    processEmbeddedDocument(indexList, documentType, documentId);
  }

  /**
   * Processing of embedded document will take the reverse direction of the relationships defined
   * in the endpoints.yml
   * For example material-sample --> collecting-event (Means that material-sample contains collecting-event)
   * So when a collecting-event is updated, we will have to look for its presence the material-sample index
   * and re-index document with that specific collecting-events embedded.
   * 
   */
  public void processEmbeddedDocument(List<String> indices, String documentType, String documentId) throws SearchApiException {

    try {

      long docCount = indexer.count(indices, documentType, documentId);

      // no paging required
      if (docCount <= ElasticSearchDocumentIndexer.ES_PAGE_SIZE) {
        processEmbeddedDocument(indexer.search(indices, documentType, documentId), documentType, documentId);
        return;
      }

      // Page through results
      SearchResponse<JsonNode> response = indexer.searchWithPIT(indices, documentType, documentId);
      boolean pageAvailable = response.hits().hits().size() != 0;
      while (pageAvailable) {
        List<DocumentInfo> documentsToIndex = new ArrayList<>();
        for (Hit<JsonNode> hit : response.hits().hits()) {
          documentsToIndex.add(jsonNodeToDocumentInfo(hit.source()));
        }
        reIndexDocuments(documentsToIndex);
        pageAvailable = false;

        int numberOfHits = response.hits().hits().size();
        // if we have a full page, try to get the next one
        if (ElasticSearchDocumentIndexer.ES_PAGE_SIZE == numberOfHits) {
          Hit<JsonNode> lastHit = response.hits().hits().get(numberOfHits - 1);
          response =
              indexer.searchAfter(response.pitId(), documentType, documentId, lastHit.sort());
          pageAvailable = true;
        }
      }

      String pitId = response.pitId();
      indexer.closePIT(pitId);

    } catch (SearchApiException e) {
      log.error("Error during re-indexing from embedded document id {} of type {}: {}", documentId, documentType, e.getMessage());
      throw e;
    }
  }

  private void processEmbeddedDocument(SearchResponse<JsonNode> embeddedDocuments, String documentType, String documentId) {
    List<DocumentInfo> documentsToIndex = processSearchResults(embeddedDocuments);
    if (!documentsToIndex.isEmpty()) {
      log.debug("re-indexing document triggered by document type:{}, id:{} update",
          documentType, documentId);
      reIndexDocuments(documentsToIndex);
    }
  }

  /**
   *
   * @param embeddedDocuments
   * @return
   */
  private List<DocumentInfo> processSearchResults(SearchResponse<JsonNode> embeddedDocuments) {
    List<DocumentInfo> documentsToIndex = new ArrayList<>();
    if (embeddedDocuments != null && embeddedDocuments.hits() != null) {
      List<Hit<JsonNode>> results = embeddedDocuments.hits().hits();
      if (!results.isEmpty()) {
        results.forEach(curHit -> {
          documentsToIndex.add(jsonNodeToDocumentInfo(curHit.source()));
        });
      }
    }
    return documentsToIndex;
  }

  private static DocumentInfo jsonNodeToDocumentInfo(JsonNode embeddedDocument) {
    JsonNode dataNode = embeddedDocument.get(JSONApiDocumentStructure.DATA);
    return new DocumentInfo(dataNode.get(JSONApiDocumentStructure.TYPE).asText(),
        dataNode.get(JSONApiDocumentStructure.ID).asText());
  }

  /**
   * Checks if a type is configured for indexing in its own index.
   * @param type
   * @return
   */
  public boolean isTypeConfigured(String type) {
    if (!svcEndpointProps.isTypeSupportedForEndpointDescriptor(type)) {
      log.debug("Unsupported endpoint type:" + type);
      return false;
    }

    // Do we have an index defined for the type ?
    if (StringUtils.isBlank(svcEndpointProps.getIndexSettingDescriptorForType(type).indexName())) {
      log.debug("Undefined index for: " + type);
      return false;
    }
    return true;
  }

  /**
   * Get a list of distinct index name that are using the provided type in relationship or
   * reverse relationship.
   * @param type
   * @return list of unique index or empty list
   */
  public List<String> getIndexForRelationshipType(String type) {
    return svcEndpointProps.
        getFilteredEndpointDescriptorStream(
            ed -> ed.containsRelationshipsType(type) || ed.containsReverseRelationshipsType(type))
        .map(IndexSettingDescriptor::indexName)
        .distinct()
        .collect(Collectors.toList());
  }

  /**
   * Triggers an indexDocument for the provided {@link DocumentInfo}
   * @param documentsToIndex document type as key and DocumentInfo as value
   */
  public void reIndexDocuments(List<DocumentInfo> documentsToIndex) {
    documentsToIndex.forEach(docInfo -> {
      // re-index the document.
      try {
        indexDocument(docInfo.type(), docInfo.id());
      } catch (SearchApiException e) {
        log.error("Document id {} of type {} could not be re-indexed. (Reason:{})", docInfo.id(), docInfo.type(),
            e.getMessage());
      }
    });
  }

  public record DocumentInfo(String type, String id) {
  }

}
