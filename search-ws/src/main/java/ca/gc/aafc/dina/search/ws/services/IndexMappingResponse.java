package ca.gc.aafc.dina.search.ws.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Singular;

import java.util.List;
import java.util.Set;

@Builder
@Getter
public class IndexMappingResponse {

  @JsonProperty("index_name")
  private String indexName;

  @Singular
  private Set<Attribute> attributes;

  @Singular
  private Set<Relationship> relationships;

  @Getter
  @EqualsAndHashCode
  @Builder
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static final class Attribute {
    private final String name;
    private final String type;
    private final Set<String> fields;
    private final String path;

    @JsonProperty("distinct_term_agg")
    private final Boolean distinctTermAgg;

    private Attribute(String name, String type, Set<String> fields, String path, Boolean distinctTermAgg) {
      this.name = name;
      this.type = type;
      this.fields = fields;
      this.path = path;
      this.distinctTermAgg = distinctTermAgg;
    }
  }

  @Getter
  @EqualsAndHashCode
  public static final class Relationship {
    private static final String REL_NAME = "type";
    private static final String REL_PATH = "included";

    private final String referencedBy;
    private final String name;
    private final String path;
    private final String value;

    private final List<Attribute> attributes;

    @Builder
    private Relationship(String value, String referencedBy, @Singular List<Attribute> attributes) {
      // name and path are always the same for relationships block
      this.name = REL_NAME;
      this.path = REL_PATH;
      this.referencedBy = referencedBy;
      this.value = value;
      this.attributes = attributes;
    }
  }

}
