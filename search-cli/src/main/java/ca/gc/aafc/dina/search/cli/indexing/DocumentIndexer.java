package ca.gc.aafc.dina.search.cli.indexing;

import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;

public interface DocumentIndexer {

  /**
   * Method takes a raw json payload and push the document into a document index using
   * the configured dina_index_document.
   * 
   * @param rawPayload
   * @throws SearchApiException
   */
  void indexDocument(String rawPayload) throws SearchApiException;

  /**
   * Release resources created by the elasticsearch client
   */
  void releaseResources();

}
