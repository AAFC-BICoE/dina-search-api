package ca.gc.aafc.dina.search.cli.indexing;

import ca.gc.aafc.dina.search.cli.config.EndpointDescriptor;
import ca.gc.aafc.dina.search.cli.config.ServiceEndpointProperties;
import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;
import ca.gc.aafc.dina.search.cli.http.OpenIDHttpClient;
import ca.gc.aafc.dina.search.cli.json.JSONApiDocumentStructure;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

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

    EndpointDescriptor endpointDescriptor = svcEndpointProps.getEndpoints().get(type);

    // Step #1: get the document
    log.info("Retrieving document id:{}", documentId);

    String documentToIndex = aClient.getDataFromUrl(endpointDescriptor, documentId);

    // Step #2: Assemble the document into a JSON map
    log.info("Assembling document id:{}", documentId);
    JsonNode jsonNode;
    try {
      jsonNode = indexableDocumentHandler.assembleDocument(documentToIndex);
    } catch (JsonProcessingException ex) {
      throw new SearchApiException("Unable to parse type '" + type + "' with the id '" + documentId + "'", ex);
    }

    // Step #3: Indexing the document into elasticsearch
    log.info("Sending document id:{} to specific index {}", documentId, endpointDescriptor.getIndexName());
    indexer.indexDocument(documentId, jsonNode, endpointDescriptor.getIndexName());

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
   * Processing of embedded document will take the reverse direction of the relationships defined
   * in the endpoints.yml
   * For example material-sample --> collecting-event (Means that material-sample contains collecting-event)
   * So when a collecting-event is updated, we will have to look for its presence the material-sample index
   * and re-index document with that specific collecting-events embedded.
   * 
   */
  public void processEmbeddedDocument(String documentType, String documentId) throws SearchApiException {

    try {
      // TODO: handle paging
      SearchResponse<JsonNode> embeddedDocuments = indexer.search(indexList, documentType, documentId);

      List<DocumentInfo> documentsToIndex = processSearchResults(embeddedDocuments);
      if (!documentsToIndex.isEmpty()) {
        log.debug("re-indexing document triggered by document type:{}, id:{} update",
            documentType, documentId);
        reIndexDocuments(documentsToIndex);
      }

    } catch (SearchApiException e) {
      log.error("Error during re-indexing from embedded document id {} of type {}: {}", documentId, documentType, e.getMessage());
      throw e;
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
          JsonNode dataNode = curHit.source().get(JSONApiDocumentStructure.DATA);
          documentsToIndex.add(new DocumentInfo(dataNode.get(JSONApiDocumentStructure.TYPE).asText(),
              dataNode.get(JSONApiDocumentStructure.ID).asText()));
        });
      }
    }
    return documentsToIndex;
  }

  /**
   * Checks if a type is configured for indexing in its own index.
   * @param type
   * @return
   */
  public boolean isTypeConfigured(String type) {
    if (!svcEndpointProps.getEndpoints().containsKey(type)) {
      log.debug("Unsupported endpoint type:" + type);
      return false;
    }

    // Do we have an index defined for the type ?
    if (StringUtils.isBlank(svcEndpointProps.getEndpoints().get(type).getIndexName())) {
      log.debug("Undefined index for: " + type);
      return false;
    }
    return true;
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

  record DocumentInfo(String type, String id) {
  }

}
