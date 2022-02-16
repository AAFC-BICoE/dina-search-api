package ca.gc.aafc.dina.search.ws.services;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import ca.gc.aafc.dina.search.ws.exceptions.SearchApiException;

import co.elastic.clients.elasticsearch.core.SearchResponse;

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
   * @param restrictedField Non null if we want to restrict based on a specific field.
   * @param restrictedFieldValue The value to filter out.
   * 
   * @return
   */
  SearchResponse<JsonNode> autoComplete(String textToMatch, String indexName, String autoCompleteField, String additionalField, String restrictedField, String restrictedFieldValue) throws SearchApiException;

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

  /**
   * Get the mapping of the provided indexName.
   * Mapping is returned as a Map where the key is like "data.attributes.displayName.type" and the value like "text"
   * @param indexName
   * @return mapping of the index
   * @throws SearchApiException if something goes wrong with the request
   */
  Map<String, String> getIndexMapping(String indexName) throws SearchApiException;

}
