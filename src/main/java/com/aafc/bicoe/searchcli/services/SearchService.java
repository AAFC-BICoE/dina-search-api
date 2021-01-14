package com.aafc.bicoe.searchcli.services;

import java.io.IOException;
import java.util.Map;

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

    private RestHighLevelClient esClient;

    @Autowired
    public SearchService(RestHighLevelClient esClient) {
        this.esClient = esClient;
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
        searchSourceBuilder.query(multiMatchQueryBuilder);

        searchRequest.source(searchSourceBuilder);

        SearchResponse response;
        try {
            response = esClient.search(searchRequest, RequestOptions.DEFAULT);

            for (SearchHit hit: response.getHits()) {
                    Map<String, Object> map = hit.getSourceAsMap();
                    for (Map.Entry<String, Object> entry : map.entrySet()) {
                        String jsonPath = entry.getKey();
                        Object entryValue = entry.getValue();
                        log.info("Key:{} : value:{}", jsonPath, entryValue);
                    }

            }
            
            return response;
        } catch (IOException ex) {
             log.error("Error in autocomplete search", ex);
             throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Error in ES search");
        }      

    }

}
