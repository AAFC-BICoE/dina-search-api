package com.aafc.bicoe.searchcli.services;

import org.elasticsearch.action.search.SearchResponse;

public interface ISearchService {

    public SearchResponse autoComplete(String prefixString, String indexName, String field);

}
