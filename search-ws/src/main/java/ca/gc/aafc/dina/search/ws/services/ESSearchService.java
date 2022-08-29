package ca.gc.aafc.dina.search.ws.services;

import ca.gc.aafc.dina.search.ws.config.MappingAttribute;
import ca.gc.aafc.dina.search.ws.config.MappingObjectAttributes;
import ca.gc.aafc.dina.search.ws.config.YAMLConfigProperties;
import ca.gc.aafc.dina.search.ws.exceptions.SearchApiException;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.indices.GetMappingResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Stack;

@Log4j2
@Service
public class ESSearchService implements SearchService {

  private static final ObjectMapper OM = new ObjectMapper();
  private static final HttpHeaders JSON_HEADERS = buildJsonHeaders();
  private static final String EMPTY_QUERY = "{\"query\":{}}";
  private static final String ID_FIELD = "data.id";
  private static final String GROUP_FIELD = "data.attributes.group.keyword"; //use the keyword version since we do a term filter

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
  public AutocompleteResponse autoComplete(String textToMatch, String indexName, String autoCompleteField,
                                           String additionalField, String group,
                                           String restrictedField, String restrictedFieldValue) throws SearchApiException {

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
    fieldsToReturn.add(ID_FIELD);
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

      if (StringUtils.isNotBlank(group)) {
        autoCompleteQueryBuilder.filter(
                QueryBuilders.term()
                        .field(GROUP_FIELD)
                        .value(v -> v.stringValue(group))
                        .build()._toQuery()
        );
      }

      if (StringUtils.isNotBlank(restrictedField) && StringUtils.isNotBlank(restrictedFieldValue)) {
        // Add restricted query filter to query builder
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
  public IndexMappingResponse getIndexMapping(String indexName) throws SearchApiException {

    IndexMappingResponse.IndexMappingResponseBuilder indexMappingResponseBuilder = IndexMappingResponse.builder();
    indexMappingResponseBuilder.indexName(indexName);

    try {
      // Retrieve the index mapping from ElasticSearch
      GetMappingResponse mappingResponse = client.indices().getMapping(builder -> builder.index(indexName));
      Map<String, Property> mappingProperties = mappingResponse.result().get(indexName).mappings().properties();

      // sanity check for JSON:API documents
      Objects.requireNonNull(mappingProperties.get("data"));
      // Extract document type
      String docType = ESResponseHelper.extractConstantKeywordValue(
              Objects.requireNonNull(mappingProperties.get("data").object().properties().get("type")));

      MappingCrawlContext crawlContext = MappingCrawlContext.builder()
              .mappingConfiguration(mappingObjectAttributes)
                      .documentType(docType).build();

      mappingProperties.forEach((propertyName, property) -> {
        Stack<String> pathStack = new Stack<>();
        pathStack.push(propertyName);
        crawlMapping(pathStack, property, crawlContext, indexMappingResponseBuilder);
      });

    } catch (IOException | ElasticsearchException e) {
      throw new SearchApiException("Error during search processing", e);
    }

    return indexMappingResponseBuilder.build();
  }

  /**
   * Crawl ElasticSearch mapping until we find the "type".
   * 
   * @param path               current path expressed as a Stack
   */
  private static void crawlMapping(Stack<String> path, Property propertyToCrawl,
                                   MappingCrawlContext crawlContext,
                                   IndexMappingResponse.IndexMappingResponseBuilder responseBuilder) {

    Map<String, Property> propertiesToProcess;
    if (propertyToCrawl.isNested()) {
      propertiesToProcess = propertyToCrawl.nested().properties();
    } else {
      propertiesToProcess = propertyToCrawl.object().properties();
    }

    propertiesToProcess.forEach((propertyName, property) -> {

      // compute the path of where we are in the mapping structure
      String currentPath = String.join(".", path);

      // Add the property to path stack.
      path.push(propertyName);

      if (property.isObject() && !property.isNested()) {
        crawlMapping(path, property, crawlContext, responseBuilder);
      } else {
        // Single Attribute
        if (currentPath.startsWith("data")) {
          if (JsonApiDocumentHelper.isRelationshipsPath(currentPath)) {
            log.debug("Attribute: {}", currentPath);
            // we need a constant_keyword in order to get the type from the ES mapping
            if (property.isConstantKeyword()) {
              String valueString = ESResponseHelper.extractConstantKeywordValue(property);
              IndexMappingResponse.Relationship rel = handleRelationshipSection(valueString, currentPath, crawlContext.getMappingConfiguration());
              if (rel != null) {
                responseBuilder.relationship(rel);
              }
            } else {
              log.debug("skipping : {}. Only constant_keyword are supported on relationships", currentPath);
            }
          } else if(JsonApiDocumentHelper.isAttributesPath(currentPath)) {
            // compute name for properties that are like determination.verbatimScientificName
            String computedPropertyName = JsonApiDocumentHelper.removeAttributesPrefix(currentPath).isBlank() ?
                            propertyName : JsonApiDocumentHelper.removeAttributesPrefix(currentPath) + "." + propertyName;

            MappingAttribute mappingAttribute = crawlContext.getMappingConfiguration()
                    .getAttribute(crawlContext.getDocumentType(), computedPropertyName);

            if(mappingAttribute != null) {
              String type = property._kind().jsonValue();
              responseBuilder.attribute(IndexMappingResponse.Attribute.builder()
                      .name(propertyName)
                      .path(currentPath)
                      .type(type)
                      .distinctTermAgg(mappingAttribute.getDistinctTermAgg())
                      .build());
            }
          } else {
            log.debug("skipping : {} : not a part of attributes or relationships", currentPath);
          }
        } else {
          log.debug("skipping : {}", currentPath);
        }
      }
      path.pop();
    });
  }

  /**
   * Builds a {@link IndexMappingResponse.Relationship}.
   *
   * @param typeKey type from the ElasticSearch mapping also matching in the {@link MappingObjectAttributes}
   * @param esPath full path from the ElasticSearch mapping (e.g. data.relationships.acMetadataCreator.data)
   * @param mappingObjectAttributes from config file
   * @return
   */
  private static IndexMappingResponse.Relationship handleRelationshipSection(String typeKey, String esPath, MappingObjectAttributes mappingObjectAttributes) {
    List<MappingAttribute> attributes = mappingObjectAttributes.getAttributes(typeKey);
    // make sure we have configuration for the relationship
    if (attributes != null) {
      IndexMappingResponse.Relationship.RelationshipBuilder relBuilder = IndexMappingResponse.Relationship.builder()
              .value(typeKey);

      relBuilder.referencedBy(StringUtils.substringBetween(esPath,"relationships.", ".data"));

      attributes.forEach(curEntry -> {
        IndexMappingResponse.Attribute.AttributeBuilder attributeBuilder = IndexMappingResponse.Attribute.builder();
        // if mappingObjectAttributes has something like determination.verbatimScientificName we only want the last part
        if (curEntry.getName().contains(".")) {
          attributeBuilder.name(StringUtils.substringAfterLast(curEntry.getName(), "."));
          attributeBuilder.path("attributes." + StringUtils.substringBeforeLast(curEntry.getName(), "."));
        } else {
          attributeBuilder.name(curEntry.getName());
          attributeBuilder.path("attributes");
        }

        attributeBuilder.type(curEntry.getType()).distinctTermAgg(curEntry.getDistinctTermAgg());

        relBuilder.attribute(attributeBuilder.build());
      });
      return relBuilder.build();
    }
    return null;
  }

}
