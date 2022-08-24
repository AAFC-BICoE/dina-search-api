package ca.gc.aafc.dina.search.ws.services;

import co.elastic.clients.elasticsearch._types.mapping.Property;

/**
 * Package protected class to help with manipulation of ElasticSearch API response.
 */
final class ESResponseHelper {


  private ESResponseHelper() {
    // utility class
  }

  /**
   * Extract the value of a Constant Keyword Property (if possible)
   *
   * @param property
   * @return the value of the Constant Keyword or null
   */
  public static String extractConstantKeywordValue(Property property) {
    if (!property.isConstantKeyword() || property.constantKeyword().value() == null) {
      return null;
    }
    return property.constantKeyword().value().to(String.class);
  }

}
