package ca.gc.aafc.dina.search.cli.config;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 *
 * @param indexName
 * @param type
 * @param relationships
 * @param relationshipsType
 * @param reverseRelationships
 * @param augmentedRelationships
 */
public record IndexSettingDescriptor(String indexName, String type,
                                     Set<String> relationships,
                                     Set<String> relationshipsType,
                                     Map<String, List<String>> optionalFields,
                                     List<ReverseRelationship> reverseRelationships,
                                     List<AugmentedRelationship> augmentedRelationships) {
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

  /**
   * Check if a relationship name is configured as an augmented relationship
   * @param relationshipName the name of the relationship to check
   * @return true if the relationship is augmented, false otherwise
   */
  public boolean isAugmentedRelationship(String relationshipName) {
    if (augmentedRelationships == null) {
      return false;
    }
    return augmentedRelationships.stream()
        .anyMatch(ar -> relationshipName.equals(ar.relationshipName()));
  }

  /**
   * Get the augmented relationship configuration for a given relationship name
   * @param relationshipName the name of the relationship
   * @return Optional containing the AugmentedRelationship if found
   */
  public Optional<AugmentedRelationship> getAugmentedRelationship(String relationshipName) {
    if (augmentedRelationships == null) {
      return Optional.empty();
    }
    return augmentedRelationships.stream()
        .filter(ar -> relationshipName.equals(ar.relationshipName()))
        .findFirst();
  }
}
