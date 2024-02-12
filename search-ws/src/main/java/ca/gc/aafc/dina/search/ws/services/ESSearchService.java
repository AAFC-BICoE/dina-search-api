package ca.gc.aafc.dina.search.ws.services;

import ca.gc.aafc.dina.jsonapi.JSONApiDocumentStructure;
import ca.gc.aafc.dina.search.ws.config.MappingAttribute;
import ca.gc.aafc.dina.search.ws.config.MappingObjectAttributes;
import ca.gc.aafc.dina.search.ws.exceptions.SearchApiException;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.indices.GetAliasResponse;
import co.elastic.clients.elasticsearch.indices.GetMappingResponse;
import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import jakarta.json.stream.JsonGenerator;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;

@Log4j2
@Service
public class ESSearchService implements SearchService {

  private static final String EMPTY_QUERY = "{\"query\":{}}";
  private static final String ID_FIELD = "data.id";
  private static final String GROUP_FIELD = "data.attributes.group.keyword"; //use the keyword version since we do a term filter

  private final ElasticsearchClient client;
  private final JsonpMapper jsonpMapper;

  private final LoadingCache<String, Optional<String>> ALIAS_CACHE;

  @Autowired
  private MappingObjectAttributes mappingObjectAttributes;
  
  public ESSearchService(@Autowired ElasticsearchClient client) {
    this.client = client;
    this.jsonpMapper = client._jsonpMapper();

    ALIAS_CACHE = Caffeine.newBuilder()
        .maximumSize(10)
        .expireAfterWrite(Duration.ofMinutes(5))
        .build(this::getIndexNameFromAlias);
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
  public String search(String indexName, String queryJson) throws SearchApiException {
    try {
      Reader strReader = new StringReader(queryJson);
      SearchRequest sr = SearchRequest.of(b -> b
              .withJson(strReader).index(indexName));

      SearchResponse<?> response = client.search(sr, JsonNode.class);
      StringWriter writer = new StringWriter();
      try (JsonGenerator generator = jsonpMapper.jsonProvider().createGenerator(writer)) {
        jsonpMapper.serialize(response, generator);
      }
      return writer.toString();
    } catch (IOException e) {
      throw new SearchApiException("Error during search processing", e);
    }
  }

  /**
   * Send a _count request to ElasticSearch using the http api since the Java Client doesn't
   * support query as json string at the moment.
   * @param indexName
   * @param queryJson Json query to forward to the elasticsearch API.
   * @return
   */
  @Override
  public CountResponse count(String indexName, String queryJson) throws SearchApiException {

    CountRequest.Builder crBuilder = new CountRequest.Builder();
    crBuilder.index(indexName);

    // we allow empty query for count where we will count all records.
    if (StringUtils.isNotBlank(queryJson) && !EMPTY_QUERY.equalsIgnoreCase(StringUtils.deleteWhitespace(queryJson))) {
      Reader strReader = new StringReader(queryJson);
      crBuilder.withJson(strReader);
    }

    CountRequest cr = crBuilder.build();
    try {
      co.elastic.clients.elasticsearch.core.CountResponse response = client.count(cr);
      return new CountResponse(response.count());
    } catch (IOException e) {
      throw new SearchApiException("Error during search processing", e);
    }
  }

  /**
   * Get the mapping definition of an index
   * @param indexNameOrAlias the name of the index or an alias pointing to an index.
   * @return
   */
  @Override
  public IndexMappingResponse getIndexMapping(String indexNameOrAlias) throws SearchApiException {

    IndexMappingResponse.IndexMappingResponseBuilder indexMappingResponseBuilder = IndexMappingResponse.builder();
    indexMappingResponseBuilder.indexName(indexNameOrAlias);

    try {
      //Check for alias
      String indexName = ALIAS_CACHE.get(indexNameOrAlias).orElse(indexNameOrAlias);

      // Retrieve the index mapping from ElasticSearch
      GetMappingResponse mappingResponse = client.indices().getMapping(builder -> builder.index(indexName));
      if (mappingResponse.result().isEmpty()) {
        throw new SearchApiException("Can't retrieve mapping of index " + indexNameOrAlias);
      }
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
   * Return the real index name based on an alias. If the alias is not found, Optional.empty is returned.
   * @param alias
   * @return first index name found for the alias or empty
   */
  private Optional<String> getIndexNameFromAlias(String alias) throws IOException {
    BooleanResponse b = client.indices().existsAlias(builder -> builder.name(alias));
    if(!b.value()) {
      return Optional.empty();
    }

    GetAliasResponse aliasResponse = client.indices().getAlias(builder -> builder.name(alias));
    return aliasResponse.result().keySet().stream().findFirst();
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
    if (propertyToCrawl.isNested()) { // included section is nested
      propertiesToProcess = propertyToCrawl.nested().properties();
    } else if (propertyToCrawl.isObject()) { // data, relationships and meta sections are object
      propertiesToProcess = propertyToCrawl.object().properties();
    } else { // not part of regular JSON:API document, skip
      return;
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
          if (JSONApiDocumentStructure.isRelationshipsPath(currentPath)) {
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
          } else if(JSONApiDocumentStructure.isAttributesPath(currentPath)) {
            IndexMappingResponse.Attribute attribute = handleDataProperty(currentPath, propertyName,
                property._kind().jsonValue(), fieldsFromProperty(property), crawlContext);
            if (attribute != null) {
              responseBuilder.attribute(attribute);
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
   * If the Property is a text, extract fields and return them as a Set otherwise return an empty set.
   * @param prop
   * @return
   */
  private static Set<String> fieldsFromProperty(Property prop) {
    if (prop.isText()) {
      return prop.text().fields().keySet();
    }
    return Set.of();
  }

  /**
   * Method responsible to create an Attribute based on path, property and configuration.
   * It will allow attributes that are defined as object to be dynamically added and others that
   * are not defined to be excluded.
   * @param currentPath
   * @param propertyName
   * @param fields optional, only used on text type
   * @param type
   * @param crawlContext
   * @return
   */
  private static IndexMappingResponse.Attribute handleDataProperty(String currentPath, String propertyName, String type, Set<String> fields,
                                                           MappingCrawlContext crawlContext) {
    // compute name for properties that are like determination.verbatimScientificName
    String computedPropertyName = JSONApiDocumentStructure.removeAttributesPrefix(currentPath).isBlank() ?
            propertyName : JSONApiDocumentStructure.removeAttributesPrefix(currentPath) + "." + propertyName;

    MappingAttribute mappingAttribute = crawlContext.getMappingConfiguration()
            .getAttribute(crawlContext.getDocumentType(), computedPropertyName);

    // if no mapping attribute is found try to match as object
    if (mappingAttribute == null) {
      mappingAttribute = crawlContext.getMappingConfiguration()
              .getObjectAttribute(crawlContext.getDocumentType(), computedPropertyName);
    }

    if (mappingAttribute != null) {
      return IndexMappingResponse.Attribute.builder()
              .name(propertyName)
              .path(currentPath)
              .type(type)
              .fields(CollectionUtils.isEmpty(fields) ? null : fields)
              .distinctTermAgg(mappingAttribute.getDistinctTermAgg())
              .subtype(mappingAttribute.getDateSubtype() != null ? mappingAttribute.getDateSubtype().toString().toLowerCase() : null)
              .build();
    }
    return null;
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
