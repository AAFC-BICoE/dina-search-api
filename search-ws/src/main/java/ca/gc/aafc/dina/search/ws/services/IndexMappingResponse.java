package ca.gc.aafc.dina.search.ws.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

public class IndexMappingResponse {

  private String indexMapping;

  private List<Attribute> attributes;
  private List<Object> relationships;

  @Getter
  @Builder
  @JsonInclude(JsonInclude.Include.NON_NULL)
  static class Attribute {
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

//    public static Attribute of(String name, String type, String path) {
//      return new Attribute(name, type, path);
//    }
  }



}
