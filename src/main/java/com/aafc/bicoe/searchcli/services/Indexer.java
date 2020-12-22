package com.aafc.bicoe.searchcli.services;

import java.io.IOException;

import com.aafc.bicoe.searchcli.jsonapi.model.DinaType;

import org.apache.http.HttpHost;
import org.elasticsearch.action.DocWriteResponse.Result;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class Indexer implements IIndexer {

    private RestHighLevelClient client;

    public Indexer() {
        client = new RestHighLevelClient(RestClient.builder(new HttpHost("localhost", 9200, "http"), 
                new HttpHost("localhost", 9201, "http")));
    }

    @Override
    public void indexDocument(DinaType dinaType, String rawPayload) {
        
        // Create index request
        IndexRequest indexRequest = this.createIndexRequest(dinaType);

        // Initialize source document
        indexRequest.source(rawPayload, XContentType.JSON);

        // Make the call to elastic..
        try {
            IndexResponse indexResponse = 
                client.index(indexRequest, RequestOptions.DEFAULT);
            
            Result operationResult = indexResponse.getResult();
            
            if (operationResult == Result.CREATED || operationResult == Result.UPDATED) {
                
                log.info("Document created in {} with id:{} and version:{}", indexResponse.getIndex(),indexResponse.getVersion(),indexResponse.getId());
            } else {
                log.error("Issue with the index operation, result:{}", operationResult);
            }

        } catch (IOException ioEx) {
            log.error("Connectivity issue with the elasticsearch server: {}", ioEx.getCause());
        }
    }


    private IndexRequest createIndexRequest(DinaType dinaType) {

        IndexRequest indexRequest = null;

        switch (dinaType) {

            case METADATA: {
                indexRequest = new IndexRequest("metadata");
            }
                break;

            case PERSON:
            case ORGANIZATION: {
                indexRequest = new IndexRequest("personinorganization");
            }
                break;
        }

        if (indexRequest == null)
            throw new IllegalArgumentException("IndexRequest could not be created");

        return indexRequest;
    }

    @Override
    public void releaseResources() {
        try {
            client.close();
            log.info("Indexer client closed");
        } catch (IOException ioEx) {
            log.error("exception during client closure...");
        }
    }
}
