package ca.gc.aafc.dina.search.ws.services;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.stream.Collectors;

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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ca.gc.aafc.dina.search.ws.config.YAMLConfigProperties;
import ca.gc.aafc.dina.search.ws.exceptions.SearchApiException;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.indices.GetMappingResponse;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
public class ESSearchService implements SearchService {

  private static final ObjectMapper OM = new ObjectMapper();
  private static final HttpHeaders JSON_HEADERS = buildJsonHeaders();

  private final ElasticsearchClient client;
  private final RestTemplate restTemplate;
  private final UriBuilder searchUriBuilder;
  
  public ESSearchService(
                  @Autowired RestTemplateBuilder builder, 
                  @Autowired ElasticsearchClient client,
                  YAMLConfigProperties yamlConfigProperties) {
    this.restTemplate = builder.build();
    this.client = client;

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
  public SearchResponse<JsonNode> autoComplete(String textToMatch, String indexName, String autoCompleteField, String additionalField) {
    
    // Based on our naming convention, we will create the expected fields to search for:
    //
    // autoCompleteField + .autocomplete
    // autoCompleteField + .autocomplete._2gram
    // autoCompleteField + .autocomplete._3gram
    //
    // additionalField (if defined)
    //
    List<String> fields = new ArrayList<>();
    List<String> fieldsToReturn = new ArrayList<>();
    fields.add(autoCompleteField);
    fieldsToReturn.add(autoCompleteField);
    fields.add(autoCompleteField + ".autocomplete._2gram");
    fields.add(autoCompleteField + ".autocomplete._3gram");

    if (StringUtils.hasText(additionalField)) {
      fields.add(additionalField);
      fieldsToReturn.add(additionalField);
    }

    String[] arrayFields = new String[fields.size()];
    fields.toArray(arrayFields);

    try {
      // Create the search response using the multi match query.
      SearchResponse<JsonNode> searchResponse = client.search(searchBuilder -> searchBuilder
          .index(indexName)
          .query(queryBuilder -> queryBuilder.multiMatch(multiMatchQuery -> multiMatchQuery
              .fields(fields)
              .query(textToMatch)
              .type(TextQueryType.BoolPrefix)))
          .source(sourceBuilder -> sourceBuilder.filter(filter -> filter.includes(fieldsToReturn))), JsonNode.class);

      return searchResponse;
    } catch (IOException ex) {
      log.error("Error in autocomplete search", ex);
    }

    return null;
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
  public Map<String, String> getIndexMapping(String indexName) throws SearchApiException {
    Map<String, String> mapping = new HashMap<>();

    try {
      // Retrieve the index mapping.
      GetMappingResponse mappingResponse = client.indices().getMapping(builder -> builder.index(indexName));

      // First level is always "properties".
      mappingResponse.result().get(indexName).mappings().properties().forEach((propertyName, property) -> {
        Stack<String> pathStack = new Stack<>();
        pathStack.push(propertyName);
        Map<String, String> pathType = new HashMap<>();
        crawlMapping(pathStack, property, pathType);
        mapping.putAll(pathType);
      });

    } catch (IOException e) {
      throw new SearchApiException("Error during index-mapping processing", e);
    }

    return mapping;
  }

  /**
   * Crawl ElasticSearch mapping until we find the "type".
   * @param path current path expressed as a Stack
   * @param esMappingStructure structure returned by the ElasticSearch client
   * @param mappingDefinition result containing the mapping and its type
   */
  private static void crawlMapping(Stack<String> path, Property propertyToCrawl, Map<String, String> mappingDefinition) {

    propertyToCrawl.object().properties().forEach((propertyName, property) -> {
      // Add path to path stack.
      path.push(propertyName);

      if (property.isObject()) {
        crawlMapping(path, property, mappingDefinition);
      } else {
        mappingDefinition.put(
            path.stream().collect(Collectors.joining(".")),
            property._kind().jsonValue());
      }

      path.pop();
    });
  }

}
