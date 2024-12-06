package ca.gc.aafc.dina.search.cli.config;

/**
 * A reverse relationship represents a relationship where another resource is pointing to the resource.
 * @param type the type of the resource (requires to have a matching ApiResourceDescriptor)
 * @param relationshipName name of the relationship to send a GET request to retrieve the resource (if there is one)
 */
public record ReverseRelationship(String type, String relationshipName) {
}
