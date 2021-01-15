package com.aafc.bicoe.searchcli.services;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.aafc.bicoe.searchcli.jsonapi.JsonSpecUtils;
import com.google.gson.JsonElement;

import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.MultiMatchQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SearchService implements ISearchService {

    private JsonSpecUtils jsonSpec;
    private RestHighLevelClient esClient;

    public SearchService(@Autowired RestHighLevelClient esClient, @Autowired JsonSpecUtils jsonSpec) {
        this.esClient = esClient;
        this.jsonSpec = jsonSpec;
    }

    public SearchResponse autoComplete(String prefixString, String indexName, String field) {

        //
        //
        // {
        // > "size": 5,
        // > "query": {
        // > "multi_match": {
        // > "query": "gend",
        // > "type": "bool_prefix",
        // > "fields": [
        // > "included.attributes.displayName.autocomplete",
        // > "included.attributes.displayName.autocomplete._2gram",
        // > "included.attributes.displayName.autocomplete._3gram"
        // > ]
        // > }
        // > }
        // >
        // }

        // Based on the POC naming convention, we will create the expcted fields:
        // field + .autocomplete
        // field + .autocomplete._2gram
        // field + .autocomplete._3gram
        //
        String[] fields = { field + ".autocomplete", field + ".autocomplete._2gram", field + ".autocomplete._3gram" };

        MultiMatchQueryBuilder multiMatchQueryBuilder = new MultiMatchQueryBuilder(prefixString, fields);

        // Boolean Prefix based request...
        //
        multiMatchQueryBuilder.type(MultiMatchQueryBuilder.Type.BOOL_PREFIX);

        SearchRequest searchRequest = new SearchRequest(indexName);

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.fetchSource(true);
        //String[] includeFields = new String[] {field + ".autocomplete", "innerObject.*"};
        //String[] excludeFields = new String[] {"test"};
        //searchSourceBuilder.fetchSource(includeFields, excludeFields);

        searchSourceBuilder.query(multiMatchQueryBuilder);
        searchRequest.source(searchSourceBuilder);

        SearchResponse searchResponse = null;
        try {
            searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
        } catch (IOException ex) {
             log.error("Error in autocomplete search {}", ex.getMessage());
        }      

        return searchResponse;
    }

}
