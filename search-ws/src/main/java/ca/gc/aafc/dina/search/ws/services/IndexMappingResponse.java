package ca.gc.aafc.dina.search.ws.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.List;

public class IndexMappingResponse {

  //private String indexMapping;

  //private List<Attribute> attributes;
  //private List<Object> relationships;

  @Getter
  @Builder
  @JsonInclude(JsonInclude.Include.NON_NULL)
  static final class Attribute {
    private final String name;
    private final String type;
    private final String path;

    @JsonProperty("distinct_term_agg")
    private final Boolean distinctTermAgg;

    private Attribute(String name, String type, String path, Boolean distinctTermAgg) {
      this.name = name;
      this.type = type;
      this.path = path;
      this.distinctTermAgg = distinctTermAgg;
    }
  }

  @Getter
  @Builder
  static final class Relationship {
    private final String name = "type";
    private final String path = "included";
    private final String value;

    @Singular
    private final List<Attribute> attributes;

    private Relationship(String value, List<Attribute> attributes) {
      this.value = value;
      this.attributes = attributes;
    }
  }

}
