package ca.gc.aafc.dina.search.ws.services;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetMappingsRequest;
import org.elasticsearch.client.indices.GetMappingsResponse;
import org.elasticsearch.cluster.metadata.MappingMetadata;
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
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriBuilder;

import ca.gc.aafc.dina.search.ws.config.YAMLConfigProperties;
import ca.gc.aafc.dina.search.ws.exceptions.SearchApiException;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
public class ESSearchService implements SearchService {

  private static final ObjectMapper OM = new ObjectMapper();
  private static final HttpHeaders JSON_HEADERS = buildJsonHeaders();

  private final RestHighLevelClient esClient;
  private final RestTemplate restTemplate;
  private final UriBuilder searchUriBuilder;
  
  public ESSearchService(
                  @Autowired RestTemplateBuilder builder, 
                  @Autowired RestHighLevelClient esClient,
                  YAMLConfigProperties yamlConfigProperties) {
    this.restTemplate = builder.build();
    this.esClient = esClient;

    // Create a URIBuilder that will be used as part of the search for documents
    // within a specific index.
    searchUriBuilder =
        new DefaultUriBuilderFactory().builder()
        .scheme(yamlConfigProperties.getElasticsearch().get("protocol"))
        .host(yamlConfigProperties.getElasticsearch().get("host"))
        .port(yamlConfigProperties.getElasticsearch().get("port"))
        .path("{indexName}/_search");

  }

  private static HttpHeaders buildJsonHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  @Override
  public SearchResponse autoComplete(String textToMatch, String indexName, String autoCompleteField, String additionalField) {
    
    // Based on our naming convention, we will create the expected fields to search for:
    //
    // autoCompleteField + .autocomplete
    // autoCompleteField + .autocomplete._2gram
    // autoCompleteField + .autocomplete._3gram
    //
    // additionalField (if defined)
    //
    List<String> fields = new ArrayList<>();
    fields.add(autoCompleteField);
    fields.add(autoCompleteField + ".autocomplete._2gram");
    fields.add(autoCompleteField + ".autocomplete._3gram");

    if (StringUtils.hasText(additionalField)) {
      fields.add(additionalField);
    }

    String[] arrayFields = new String[fields.size()];
    fields.toArray(arrayFields);

    MultiMatchQueryBuilder multiMatchQueryBuilder = new MultiMatchQueryBuilder(textToMatch, arrayFields);

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
      log.error("Error in autocomplete search", ex);
    }

    return searchResponse;
  }

  @Override
  public String search(String indexName, String query) throws SearchApiException {

    JsonNode jsonNode;
    try {
      jsonNode = OM.readTree(query);
      URI uri = searchUriBuilder.build(Map.of("indexName", indexName));

      HttpEntity<?> entity = new HttpEntity<>(jsonNode.toString(), JSON_HEADERS);
      ResponseEntity<String> searchResponse = restTemplate.exchange(uri, HttpMethod.POST, entity, String.class);

      return searchResponse.getBody();

    } catch (JsonProcessingException e) {
      throw new SearchApiException("Error during search processing", e);
    }
  }

  @Override
  public String getIndexMapping(String indexName) throws SearchApiException {

    GetMappingsRequest mappingRequest = new GetMappingsRequest().indices(indexName);
    Map<String, Object> mapping;
    try {
      GetMappingsResponse getMappingResponse = esClient.indices().getMapping(mappingRequest, RequestOptions.DEFAULT);
      // since we only asked for a single index we can get it from the result
      MappingMetadata mappingMetadata = getMappingResponse.mappings().get(indexName);

      // Filter to only send term and type
      // email={type=text, fields={keyword={ignore_above=256, type=keyword}}}}} we want to only keep email={type=text}
      mapping = mappingMetadata.getSourceAsMap();
      
    } catch (IOException e) {
      throw new SearchApiException("Error during search processing", e);
    }

    //TODO more processing to remove some noise
    return Objects.toString(mapping);

  }

}
