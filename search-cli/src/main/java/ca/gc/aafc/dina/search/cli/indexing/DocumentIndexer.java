package ca.gc.aafc.dina.search.cli.indexing;

import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;

public interface DocumentIndexer {

  /**
   * Method takes a raw json payload and push the document into a document index using
   * the configured dina_index_document.
   * 
   * @param documentId Document identifier to be pass to the indexer
   * @param rawPayload Document to be indexed
   * 
   * @throws SearchApiException
   */
  OperationStatus indexDocument(String documentId, String rawPayload) throws SearchApiException;

    /**
   * Method takes a raw json payload and push the document into the index provided.
   * 
   * @param documentId Document identifier to be pass to the indexer
   * @param rawPayload Document ot be indexed
   * 
   * @param indexName Elasticsearch index to use for the document.
   * @throws SearchApiException
   */
  OperationStatus indexDocument(String documentId, String rawPayload, String indexName) throws SearchApiException;

  /**
   * Delete the document identified by the documentId from the default index.
   * 
   * @param documentId Document identifier to be pass to the indexer
   * 
   * @throws SearchApiException
   */
  void deleteDocument(String documentId) throws SearchApiException;

  /**
   * Delete the document identified by the documentId from all supported indices.
   * 
   * 
   * @param documentId Document identifier to be pass to the indexer
   * @param indexName Index containing the document to be deleted
   * 
   * @throws SearchApiException
   */
  void deleteDocument(String documentId, String indexName) throws SearchApiException;
  
  /**
   * Release resources created by the elasticsearch client
   */
  void releaseResources();

}
