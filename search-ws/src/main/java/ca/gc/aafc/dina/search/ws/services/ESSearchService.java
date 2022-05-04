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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriBuilder;

import org.apache.commons.lang3.StringUtils;

import ca.gc.aafc.dina.search.ws.config.MappingAttribute;
import ca.gc.aafc.dina.search.ws.config.MappingObjectAttributes;
import ca.gc.aafc.dina.search.ws.config.YAMLConfigProperties;
import ca.gc.aafc.dina.search.ws.exceptions.SearchApiException;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.PropertyVariant;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.indices.GetMappingResponse;
import jakarta.json.JsonValue;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
public class ESSearchService implements SearchService {

  private static final ObjectMapper OM = new ObjectMapper();
  private static final HttpHeaders JSON_HEADERS = buildJsonHeaders();
  private static final String EMPTY_QUERY = "{\"query\":{}}";

  private final ElasticsearchClient client;
  private final RestTemplate restTemplate;

  // URI for low level ElasticSearch client
  private final UriBuilder searchUriBuilder;
  private final UriBuilder countUriBuilder;

  @Autowired
  private MappingObjectAttributes mappingObjectAttributes;
  
  public ESSearchService(
                  @Autowired RestTemplateBuilder builder, 
                  @Autowired ElasticsearchClient client,
                  YAMLConfigProperties yamlConfigProperties) {
    this.restTemplate = builder.build();
    this.client = client;

    // Create a URIBuilder that will be used as part of the search for documents
    // within a specific index.
    searchUriBuilder = prepareESUriBuilder(yamlConfigProperties, "_search");
    countUriBuilder = prepareESUriBuilder(yamlConfigProperties, "_count");
  }

  private static UriBuilder prepareESUriBuilder(YAMLConfigProperties yamlConfigProperties, String esEndpoint) {
    return new DefaultUriBuilderFactory().builder()
        .scheme(yamlConfigProperties.getElasticsearch().get("protocol"))
        .host(yamlConfigProperties.getElasticsearch().get("host"))
        .port(yamlConfigProperties.getElasticsearch().get("port"))
        .path("{indexName}/" + esEndpoint);
  }

  private static HttpHeaders buildJsonHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }


  @Override
  public AutocompleteResponse autoComplete(String textToMatch, String indexName, String autoCompleteField, String additionalField, String restrictedField, String restrictedFieldValue) throws SearchApiException {

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

    if (StringUtils.isNotBlank(additionalField)) {
      fields.add(additionalField);
      fieldsToReturn.add(additionalField);
    }

    String[] arrayFields = new String[fields.size()];
    fields.toArray(arrayFields);

    try {
      // Query builder with Multimatch for the search_as_you_type field
      BoolQuery.Builder autoCompleteQueryBuilder = QueryBuilders.bool().must(
          QueryBuilders.multiMatch()
              .fields(fields)
              .query(textToMatch)
              .type(TextQueryType.BoolPrefix)
              .build()._toQuery()
      );

      if (StringUtils.isNotBlank(restrictedField) && StringUtils.isNotBlank(restrictedFieldValue)) {
        // Add restricted query filter to query builder.
        autoCompleteQueryBuilder.filter(
            QueryBuilders.term()
                .field(restrictedField)
                .value(v -> v.stringValue(restrictedFieldValue))
                .build()._toQuery()
        );
      }

      return AutocompleteResponse.fromSearchResponse(
          client.search(searchBuilder -> searchBuilder
          .index(indexName)
          .query(autoCompleteQueryBuilder.build()._toQuery())
          .storedFields(fieldsToReturn)
          .source(sourceBuilder -> sourceBuilder.filter(filter -> filter.includes(fieldsToReturn))), JsonNode.class));

    } catch (IOException ex) {
      throw new SearchApiException("Error during search processing", ex);
    }
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

  /**
   * Send a _count request to ElasticSearch using the http api since the Java Client doesn't
   * support query as json string at the moment.
   * @param indexName
   * @param query Json query to forward to the elasticsearch API.
   * @return
   */
  @Override
  public CountResponse count(String indexName, String query) {

    URI uri = countUriBuilder.build(Map.of("indexName", indexName));

    // Accept empty query but don't forward it to ElasticSearch
    if (EMPTY_QUERY.equalsIgnoreCase(StringUtils.deleteWhitespace(query))) {
      HttpEntity<?> entity = new HttpEntity<>(JSON_HEADERS);
      return restTemplate.exchange(uri, HttpMethod.GET, entity, CountResponse.class).getBody();
    }

    HttpEntity<?> entity = new HttpEntity<>(query, JSON_HEADERS);
    ResponseEntity<CountResponse> countResponse = restTemplate.exchange(uri, HttpMethod.POST, entity, CountResponse.class);

    return countResponse.getBody();
  }

  @Override
  public ResponseEntity<JsonNode> getIndexMapping(String indexName) throws SearchApiException {

    Map<String, String> data = new HashMap<>();
    Map<String, String> included = new HashMap<>();
    Map<String, String> relationships = new HashMap<>();

    ObjectNode indexMappingNode = OM.createObjectNode();
    indexMappingNode.put("indexName", indexName);

    try {

      // Retrieve the index mapping.
      GetMappingResponse mappingResponse = client.indices().getMapping(builder -> builder.index(indexName));

      // First level is always "properties".
      mappingResponse.result().get(indexName).mappings().properties().forEach((propertyName, property) -> {

        Stack<String> pathStack = new Stack<>();
        pathStack.push(propertyName);

        crawlMapping(pathStack, property, data, included, relationships);

        // Add all document attributes
        ArrayNode documentAttributes = indexMappingNode.putArray("attributes");
        data.entrySet().forEach(curEntry -> {
          ObjectNode curJsonAttribute = setJsonNode(curEntry.getKey(), curEntry.getValue(), false, null);
          documentAttributes.add(curJsonAttribute);
        });

        // Attributes from included relationships
        //
        ArrayNode relationshipContainer = indexMappingNode.putArray("relationships");
        relationships.entrySet().forEach(curKey -> {

          if (curKey.getKey().endsWith("data.type.value")) {

            ObjectNode curRelationship = OM.createObjectNode();

            curRelationship.put("name", "type");
            curRelationship.put("value",curKey.getValue());           
            curRelationship.put("path", "included");
            relationshipContainer.add(curRelationship);

            // Add attributes for the relationship
            List<MappingAttribute> attributes  = mappingObjectAttributes.getMappings().get(curKey.getValue());
            
            if (attributes != null) {
              ArrayNode relationShipAttributes = curRelationship.putArray("attributes");

              attributes.forEach(curEntry -> {
                ObjectNode curJsonAttribute = setJsonNode(curEntry.getName(), curEntry.getType(), true, curEntry.getDistinctTermAgg());
                relationShipAttributes.add(curJsonAttribute);     
              });
            }

          }
        });
      });

    } catch (IOException | ElasticsearchException e) {
      log.error("Error during index-mapping processing", e);
      return new ResponseEntity<>(indexMappingNode, HttpStatus.BAD_REQUEST);
    }

    return ResponseEntity.ok().body(indexMappingNode);
  }

  private ObjectNode setJsonNode(String key, String type, boolean isIncludedSection, Boolean distinctTermAgg) {

    IndexMappingResponse.Attribute.AttributeBuilder attributeBuilder = IndexMappingResponse.Attribute.builder();

    attributeBuilder.name(key.substring(key.lastIndexOf(".") + 1));
    attributeBuilder.type(type);

    int startPos = 0;
    if (key.startsWith("included.")) {
      startPos = "included.".length();
    }

    String path = "";
    if (key.substring(startPos).contains(".")) {
      path = key.substring(startPos, key.lastIndexOf("."));
    }

    if (isIncludedSection) {
      path = "attributes" + (!path.isEmpty() ? "." + path : path); 
    }

    attributeBuilder.distinctTermAgg(distinctTermAgg);
    attributeBuilder.path(path);

    return OM.convertValue(attributeBuilder.build(), ObjectNode.class);
  }

  /**
   * Crawl ElasticSearch mapping until we find the "type".
   * 
   * @param path               current path expressed as a Stack
   */
  private static void crawlMapping(Stack<String> path, Property propertyToCrawl,
      Map<String, String> data,
      Map<String, String> included,
      Map<String, String> relationships) {

    Map<String, Property> propertiesToProcess = null;
    if (propertyToCrawl.isNested()) {
      propertiesToProcess = propertyToCrawl.nested().properties();
    } else {
      propertiesToProcess = propertyToCrawl.object().properties();
    }

    propertiesToProcess.forEach((propertyName, property) -> {

      // Add path to path stack.
      path.push(propertyName);

      if (property.isObject() && !property.isNested()) {
        crawlMapping(path, property, data, included, relationships);
      } else {

        String attributeName = path.stream().collect(Collectors.joining("."));
        String type = property._kind().jsonValue();
        PropertyVariant theProperty = property._get();

        // Single Attribute
        //
        if (attributeName.startsWith("data")) {
          if (attributeName.startsWith("data.relationships")) {
            
            log.debug("Attribute: {}", attributeName);
            String valueString = "";
            if (theProperty._toProperty().isConstantKeyword()) {
              JsonValue value = theProperty._toProperty().constantKeyword().value().toJson();
              valueString = value.toString().replace("\"", "");
            } else if (theProperty._toProperty().isKeyword()) {
              valueString = theProperty._toProperty().keyword().name();
            } else if (theProperty._toProperty().isText()) {
              valueString = theProperty._toProperty().text().name();
            } else {
              valueString = theProperty._toProperty().toString();
            }
            
            if (valueString != null) {
              relationships.put(attributeName, type);
              relationships.put(attributeName + ".value", valueString);
            }
          } else {
            data.put(attributeName, type);
          }
        } else if (attributeName.startsWith("included")) {
          included.put(attributeName, type);
        }
      }
      path.pop();
    });
  }

}
