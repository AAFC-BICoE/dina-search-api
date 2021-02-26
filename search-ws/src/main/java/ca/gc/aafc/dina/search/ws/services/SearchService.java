package ca.gc.aafc.dina.search.ws.services;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.MultiMatchQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import ca.gc.aafc.dina.search.ws.config.YAMLConfigProperties;
import ca.gc.aafc.dina.search.ws.exceptions.SearchApiException;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
public class SearchService implements ISearchService {

  private static final ObjectMapper OM = new ObjectMapper();

  private final RestHighLevelClient esClient;
  private final RestTemplate restTemplate;
  private final String baseUrlTemplate;

  private final HttpHeaders JSON_HEADERS = buildJsonHeaders();

  public SearchService(
                  @Autowired RestTemplateBuilder builder, 
                  @Autowired RestHighLevelClient esClient,
                  YAMLConfigProperties yamlConfigProperties) {
    this.restTemplate = builder.build();
    this.esClient = esClient;

    // Create a single line template that will be used as part of the search for documents
    // within a specific index.
    //
    baseUrlTemplate =
      yamlConfigProperties.getElasticsearch().get("protocol") +
      "://" + yamlConfigProperties.getElasticsearch().get("host") +
      ":" + yamlConfigProperties.getElasticsearch().get("port") +
      "/@Index_Token@" +
      "/_search";

  }

  private static HttpHeaders buildJsonHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  @Override
  public SearchResponse autoComplete(String prefixString, String indexName, String field) {

    // Based on our naming convention, we will create the expected fields to search for:
    //
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

    searchSourceBuilder.query(multiMatchQueryBuilder);
    searchRequest.source(searchSourceBuilder);

    SearchResponse searchResponse = null;
    try {
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException ex) {
      log.error("Error in autocomplete search {}", ex);
    }

    return searchResponse;
  }

  @Override
  public String search(String indexName, String query) throws SearchApiException {

    JsonNode jsonNode;
    try {
      jsonNode = OM.readTree(query);
      URI uri = new URI(baseUrlTemplate.replace("@Index_Token@", indexName));

      HttpEntity<?> entity = new HttpEntity<>(jsonNode.toString(), JSON_HEADERS);
      ResponseEntity<String> searchResponse = restTemplate.exchange(uri, HttpMethod.POST, entity, String.class);

      return searchResponse.getBody();

    } catch (JsonProcessingException | URISyntaxException e) {
      throw new SearchApiException("Error during search processing", e);
    }
  }
}
