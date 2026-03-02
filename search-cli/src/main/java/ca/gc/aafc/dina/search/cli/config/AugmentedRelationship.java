package ca.gc.aafc.dina.search.cli.config;

import java.util.List;

/**
 * Represents an augmented relationship configuration where nested relationships should be
 * included up to one level deep.
 * 
 * For example, if a material-sample has a collectingEvent relationship, and collectingEvent
 * has collectors, this configuration allows including both collectingEvent and its collectors
 * in the material-sample's included section.
 * 
 * The included section of augmented documents is stripped to prevent mapping explosion.
 * 
 * @param relationshipName the name of the relationship from the parent document (e.g., "collectingEvent")
 * @param nestedRelationships list of nested relationship names to include from the augmented document (e.g., ["collectors"])
 */
public record AugmentedRelationship(String relationshipName, List<String> nestedRelationships) {
}
