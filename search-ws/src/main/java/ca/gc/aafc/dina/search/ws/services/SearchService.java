package ca.gc.aafc.dina.search.ws.services;

import org.elasticsearch.action.search.SearchResponse;
import ca.gc.aafc.dina.search.ws.exceptions.SearchApiException;

public interface SearchService {

  /**
   * The autoComplete is to be used to return documents matching the textToMatch.
   * 
   * We are using Search_as_you_type type that make use of three subfields.
   * 
   * More details: https://coralogix.com/log-analytics-blog/elasticsearch-autocomplete-with-search-as-you-type/
   * 
   * For example field title would have been indexed during the text analysis as followed:
   * 
   * title
   *    title._2gram
   *    title._3gram
   *    title._index_prefix
   * 
   * and for the query:
   * 
   * "query": {
   *   "multi_match": {
   *      "query": "Sta",
   *      "type": "bool_prefix",
   *      "fields": [
   *         "title",
   *         "title._2gram",
   *         "title._3gram"
   *       ]
   *    }
   *  }
   * 
   * 
   * @param textToMatch Text to match
   * @param indexName Index for the documents
   * @param autoCompleteField target field for the auto complete search.
   * @param additionalField additional or alternate field to evaluate in addition to the autoCompleteField.
   * @return
   */
  SearchResponse autoComplete(String textToMatch, String indexName, String autoCompleteField, String additionalField);

  /**
   * Search will take the provided json text query and forward it to the configured
   * elasticsearch search API.
   *  
   * @param indexName Index name to use for the search.
   * @param query JSOn query to forward to the elasticsearch API.
   * 
   * @return JSOn return from the elasticsearch query
   * @throws SearchApiException in case of connectivity issues and/or malformed queries.
   * 
   */
  String search(String indexName, String query) throws SearchApiException;

  String getIndexMapping(String indexName) throws SearchApiException;

}
