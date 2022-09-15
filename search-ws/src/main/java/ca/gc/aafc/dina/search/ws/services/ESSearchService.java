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
import co.elastic.clients.elasticsearch.indices.GetMappingResponse;
import co.elastic.clients.json.JsonpMapper;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.json.stream.JsonGenerator;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Stack;

@Log4j2
@Service
public class ESSearchService implements SearchService {

  private static final String EMPTY_QUERY = "{\"query\":{}}";
  private static final String ID_FIELD = "data.id";
  private static final String GROUP_FIELD = "data.attributes.group.keyword"; //use the keyword version since we do a term filter

  private final ElasticsearchClient client;
  private final JsonpMapper jsonpMapper;

  @Autowired
  private MappingObjectAttributes mappingObjectAttributes;
  
  public ESSearchService(@Autowired ElasticsearchClient client) {
    this.client = client;
    this.jsonpMapper = client._jsonpMapper();
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
            // compute name for properties that are like determination.verbatimScientificName
            String computedPropertyName = JSONApiDocumentStructure.removeAttributesPrefix(currentPath).isBlank() ?
                            propertyName : JSONApiDocumentStructure.removeAttributesPrefix(currentPath) + "." + propertyName;

            MappingAttribute mappingAttribute = crawlContext.getMappingConfiguration()
                    .getAttribute(crawlContext.getDocumentType(), computedPropertyName);

            // if no mapping attribute is found try to match as object
            if(mappingAttribute == null) {
              mappingAttribute = crawlContext.getMappingConfiguration()
                      .getObjectAttribute(crawlContext.getDocumentType(), computedPropertyName);
            }

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
