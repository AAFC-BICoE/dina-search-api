package ca.gc.aafc.dina.search.cli.config;

import java.util.List;
import java.util.Set;

/**
 *
 * @param indexName
 * @param type
 * @param relationships
 * @param relationshipsType
 */
public record IndexSettingDescriptor(String indexName, String type,
                                     Set<String> relationships,
                                     Set<String> relationshipsType,
                                     List<ReverseRelationship> reverseRelationships) {
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
