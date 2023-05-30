package ca.gc.aafc.dina.search.cli.indexing;

import ca.gc.aafc.dina.search.cli.commands.messaging.DocumentInfo;
import ca.gc.aafc.dina.search.cli.config.EndpointDescriptor;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    svcEndpointProps.getEndpoints().values().forEach(desc -> {
      if (StringUtils.isNotBlank(desc.getIndexName())) {
        indexList.add(desc.getIndexName());
      }
    });

  }

  /**
   * Index or re-index the document identified by the type and documentId
   */
  public JsonNode indexDocument(String type, String documentId) throws SearchApiException {
    JsonNode jsonNode;
    if (!svcEndpointProps.getEndpoints().containsKey(type)) {
      throw new SearchApiException("Unsupported endpoint type:" + type);
    }

    // Step #1: get the document
    log.info("Retrieving document id:{}", documentId);
    EndpointDescriptor endpointDescriptor = svcEndpointProps.getEndpoints().get(type);
    String documentToIndex = aClient.getDataFromUrl(endpointDescriptor, documentId);

    // Step #2: Assemble the document into a JSON map
    log.info("Assembling document id:{}", documentId);

    try {
      jsonNode = indexableDocumentHandler.assembleDocument(documentToIndex);
    } catch (JsonProcessingException ex) {
      throw new SearchApiException("Unable to parse type '" + type + "' with the id '" + documentId + "'", ex);
    }

    // Step #3: Indexing the document into elasticsearch
    if (StringUtils.isNotBlank(endpointDescriptor.getIndexName())) {
      log.info("Sending document id:{} to specific index {}", documentId, endpointDescriptor.getIndexName());
      indexer.indexDocument(documentId, jsonNode, endpointDescriptor.getIndexName());
    }

    return jsonNode;
  }

  public String deleteDocument(String type, String documentId) throws SearchApiException {

    String processedMessage = null;
    if (!svcEndpointProps.getEndpoints().containsKey(type)) {
      processedMessage = "Unsupported endpoint type:" + type;
      log.error(processedMessage);
      return processedMessage;
    }

    EndpointDescriptor endpointDescriptor = svcEndpointProps.getEndpoints().get(type);

    // Step #2: Delete the document from elasticsearch
    if (StringUtils.isNotBlank(endpointDescriptor.getIndexName())) {
      log.info("Deleting document id:{} from specific index {}", documentId, endpointDescriptor.getIndexName());
      indexer.deleteDocument(documentId, endpointDescriptor.getIndexName());
    }
    
    return processedMessage;
  }

  /**
   * Processing of embedded dcoument will take the reverse direction of the relationships defined
   * in the endpoints.yml
   * For example material-sample --> collecting-event (Means that material-sample contains collecting-event)
   * So when a collecting-event is updated, we will have to look for its presence the material-sample index
   * and re-index document with that specific collecting-events embedded.
   * 
   */
  public void processEmbeddedDocument(String documentType, String documentId) throws SearchApiException {

    try {

      SearchResponse<JsonNode> embeddedDocuments = indexer.search(indexList, documentType, documentId);

      Map<String, DocumentInfo> mapIdToType = processSearchResults(embeddedDocuments);

      // mapTypeToId, contains the list of documents for reindexing.
      if (!mapIdToType.isEmpty()) {
        log.debug("re-indexing document triggered by document type:{}, id:{} update",
            documentType, documentId);
        reIndexDocuments(mapIdToType);
      }

    } catch (SearchApiException e) {
      log.error("Error during re-indexing from embedded document id {} of type {}: {}", documentId, documentType, e.getMessage());
      throw e;
    }
  }

  private Map<String, DocumentInfo> processSearchResults(SearchResponse<JsonNode> embeddedDocuments) {
    Map<String, DocumentInfo> mapIdToType = new HashMap<>();
    if (embeddedDocuments != null && embeddedDocuments.hits() != null) {
      List<Hit<JsonNode>> results = embeddedDocuments.hits().hits();
      if (!results.isEmpty()) {
        results.forEach(curHit -> {
          mapIdToType.put(
            curHit.source().get("data").get("id").asText(), 
            new DocumentInfo(
              curHit.source().get("data").get("type").asText(),
              curHit.index()));
        });
      }
    }
    return mapIdToType;
  }

  /**
   * Triggers an indexDocument for the provided type/uuid
   * @param mapIdToType document type as key and DocumentInfo as value
   */
  public void reIndexDocuments(Map<String, DocumentInfo> mapIdToType) {
    mapIdToType.forEach((key, value) -> {
      // re-index the document.
      try {
        indexDocument(value.getType(), key);
      } catch (SearchApiException e) {
        log.error("Document id {} of type {} could not be re-indexed. (Reason:{})", key, value,
            e.getMessage());
      }
    });
  }

}
