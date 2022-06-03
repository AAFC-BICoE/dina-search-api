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
  static final class Relationship {
    private static final String REL_NAME = "type";
    private static final String REL_PATH = "included";

    private final String name;
    private final String path;
    private final String value;

    private final List<Attribute> attributes;

    @Builder
    private Relationship(String value, @Singular List<Attribute> attributes) {
      // name and path are always the same for relationships block
      this.name = REL_NAME;
      this.path = REL_PATH;
      this.value = value;
      this.attributes = attributes;
    }
  }

}
