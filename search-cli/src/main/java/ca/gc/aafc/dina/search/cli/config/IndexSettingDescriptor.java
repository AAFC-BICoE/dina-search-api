package ca.gc.aafc.dina.search.cli.config;

import java.util.List;
import java.util.Set;

public record IndexSettingDescriptor(String indexName, String type,
                                     List<String> relationships, Set<String> relationshipsType) {
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
