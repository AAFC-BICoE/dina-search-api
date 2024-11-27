package ca.gc.aafc.dina.search.cli.config;

import java.util.List;
import java.util.Set;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Data
public class EndpointDescriptor {
  private String indexName;
  private String type;
  private List<String> relationships;
  private Set<String> relationshipsType;

  /**
   * null-safe contains for relationshipsType
   * @param type
   * @return
   */
  public boolean containsRelationshipsType(String type) {
    if (relationshipsType == null) {
      return false;
    }
    return relationshipsType.contains(type);
  }
}
