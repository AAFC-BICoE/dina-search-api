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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.indices.GetMappingResponse;
import co.elastic.clients.elasticsearch.indices.get_mapping.IndexMappingRecord;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
public class ESSearchService implements SearchService {

  private static final ObjectMapper OM = new ObjectMapper();
  private static final HttpHeaders JSON_HEADERS = buildJsonHeaders();
  private static final String ES_MAPPING_PROPERTIES = "properties";
  private static final String ES_MAPPING_TYPE = "type";
  private static final String ES_MAPPING_FIELDS = "fields";

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
  public SearchResponse<?> autoComplete(String textToMatch, String indexName, String autoCompleteField, String additionalField) {
    
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
      // Generate multi match query.
      MultiMatchQuery multiMatchQuery = MultiMatchQuery.of(queryBuilder -> queryBuilder
        .fields(fields)
        .query(textToMatch)
        .type(TextQueryType.BoolPrefix)
      );

      // Create the search response using the multi match query.
      SearchResponse<?> searchResponse = client.search(searchBuilder -> searchBuilder
        .index(indexName)
        .query(queryBuilder -> queryBuilder.multiMatch(multiMatchQuery))
      , null);

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
  @SuppressWarnings("unchecked")
  public Map<String, String> getIndexMapping(String indexName) throws SearchApiException {
    Map<String, String> mapping = new HashMap<>();

    try {
      GetMappingResponse mappingResponse = client.indices().getMapping(builder -> builder.index(indexName));
      IndexMappingRecord indexMap = mappingResponse.get(indexName);

    } catch (ElasticsearchException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    /*
    try {

      // since we only asked for a single index we can get it from the result
      MappingMetadata mappingMetadata = getMappingResponse.mappings().get(indexName);

      Map<String, Object> esMapping = mappingMetadata.getSourceAsMap();

      // First level is always "properties"
      Map<String, Object> properties = (Map<String, Object>) esMapping.get(ES_MAPPING_PROPERTIES);
      for (Map.Entry<String, Object> entry : properties.entrySet()) {
        Stack<String> pathStack = new Stack<>();
        pathStack.push(entry.getKey());
        Map<String, String> pathType = new HashMap<>();
        crawlMapping(pathStack, (Map<String, Object>) entry.getValue(), pathType);
        mapping.putAll(pathType);
      }
    } catch (IOException | ElasticsearchStatusException e) {
      throw new SearchApiException("Error during index-mapping processing", e);
    }
    */
    return mapping;

  }

  /**
   * Crawl ElasticSearch mapping until we find the "type".
   * @param path current path expressed as a Stack
   * @param esMappingStructure structure returned by the ElasticSearch client
   * @param mappingDefinition result containing the mapping and its type
   */
  @SuppressWarnings("unchecked")
  private static void crawlMapping(Stack<String> path, Map<String, Object> esMappingStructure, Map<String, String> mappingDefinition) {
    for (Map.Entry<String, Object> propEntry : esMappingStructure.entrySet()) {
      // skip "fields" for now
      if (!ES_MAPPING_FIELDS.equals(propEntry.getKey())) {
        path.push(propEntry.getKey());
        if (propEntry.getValue() instanceof Map) {
          crawlMapping(path, (Map<String, Object>) propEntry.getValue(), mappingDefinition);
        } else {
          // we only record leaf "type"
          if (ES_MAPPING_TYPE.equals(propEntry.getKey())) {
            mappingDefinition.put(path.stream().filter(s -> !s.equals(ES_MAPPING_PROPERTIES))
                .collect(Collectors.joining(".")), propEntry.getValue().toString());
          }
        }
        path.pop();
      }
    }
  }

}
