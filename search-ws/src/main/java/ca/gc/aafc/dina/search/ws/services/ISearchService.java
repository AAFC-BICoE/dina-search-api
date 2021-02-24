package ca.gc.aafc.dina.search.ws.services;

import org.elasticsearch.action.search.SearchResponse;

import ca.gc.aafc.dina.search.ws.exceptions.SearchApiException;

public interface ISearchService {

  SearchResponse autoComplete(String prefixString, String indexName, String field);

  String search(String indexName, String query) throws SearchApiException;

}
