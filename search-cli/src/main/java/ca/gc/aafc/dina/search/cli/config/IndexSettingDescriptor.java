package ca.gc.aafc.dina.search.cli.config;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @param indexName
 * @param type
 * @param relationships
 * @param relationshipsType
 * @param reverseRelationships
 */
public record IndexSettingDescriptor(String indexName, String type,
                                     Set<String> relationships,
                                     Set<String> relationshipsType,
                                     Map<String, List<String>> optionalFields,
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

  /**
   * null-safe contains for reverseRelationships
   * @param type
   * @return
   */
  public boolean containsReverseRelationshipsType(String type) {
    if (reverseRelationships == null) {
      return false;
    }
    return reverseRelationships.stream().anyMatch(rr -> type.equals(rr.type()));
  }
}
