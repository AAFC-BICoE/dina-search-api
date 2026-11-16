package ca.gc.aafc.dina.search.ws.services;

import co.elastic.clients.elasticsearch.core.SearchResponse;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom class that mimics ElasticSearch SearchResponse with only the fields that are relevant.
 */
@RequiredArgsConstructor
@Getter
public class AutocompleteResponse {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final List<Hit> hits;

  @Builder
  @Getter
  public static class Hit {
    private final String index;
    private final String documentId;
    private final Double score;
    private final Object source;
  }

  /**
   * Creates an {@link AutocompleteResponse} from ElasticSearch {@link SearchResponse}.
   * @param sr
   * @return
   */
  public static AutocompleteResponse fromSearchResponse(SearchResponse<?> sr) {
    List<Hit> hits = new ArrayList<>();
    for (co.elastic.clients.elasticsearch.core.search.Hit<?> hit : sr.hits().hits()) {
      hits.add(Hit.builder()
          .index(hit.index())
          .documentId(hit.id())
          .score(hit.score())
          .source(toApiSource(hit.source()))
          .build());
    }
    return new AutocompleteResponse(hits);
  }

  private static Object toApiSource(Object source) {
    if (source instanceof com.fasterxml.jackson.databind.JsonNode jsonNode) {
      // Convert Elasticsearch's Jackson 2 source node to Jackson 3 for the API response.
      return OBJECT_MAPPER.readTree(jsonNode.toString());
    }
    return source;
  }

}
