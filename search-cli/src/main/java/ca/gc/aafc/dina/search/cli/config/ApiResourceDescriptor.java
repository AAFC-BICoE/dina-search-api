package ca.gc.aafc.dina.search.cli.config;

/**
 * Contains information about how to reach a specific resource API
 * @param type the json:api type
 * @param url the url to reach the resource's API
 */
public record ApiResourceDescriptor(String type, String url) {
}
