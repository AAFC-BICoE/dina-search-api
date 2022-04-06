package ca.gc.aafc.dina.search.cli.indexing;

import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;

public interface DocumentIndexer {

    /**
   * Method takes a raw json payload and push the document into the index provided.
   * 
   * @param documentId Document identifier to be pass to the indexer
   * @param payload Document to be indexed
   * 
   * @param indexName Elasticsearch index to use for the document.
   * @throws SearchApiException
   */
  OperationStatus indexDocument(String documentId, Object payload, String indexName) throws SearchApiException;

  /**
   * Delete the document identified by the documentId from all supported indices.
   * 
   * 
   * @param documentId Document identifier to be pass to the indexer
   * @param indexName Index containing the document to be deleted
   * 
   * @throws SearchApiException
   */
  OperationStatus deleteDocument(String documentId, String indexName) throws SearchApiException;

  /**
   * Release resources created by the elasticsearch client
   */
  void releaseResources();
}
