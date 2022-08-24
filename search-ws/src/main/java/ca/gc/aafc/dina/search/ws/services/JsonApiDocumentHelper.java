package ca.gc.aafc.dina.search.ws.services;

import org.apache.commons.lang3.StringUtils;

/**
 * Package protected class to help with manipulation of JSON:API documents
 * Should be combined with JSONApiDocumentStructure and moved to dina-base
 */
final class JsonApiDocumentHelper {

  public static final String DATA = "data";
  public static final String DATA_ATTRIBUTES = "data.attributes";
  //private static final String DATA_ATTRIBUTES_DOT = "data.attributes.";

  public static final String DATA_RELATIONSHIPS = "data.relationships";

  private JsonApiDocumentHelper() {
    //utility class
  }

  static boolean isRelationshipsPath(String currentPath) {
    return currentPath.startsWith(DATA_RELATIONSHIPS);
  }

  static boolean isAttributesPath(String currentPath) {
    return currentPath.startsWith(DATA_ATTRIBUTES);
  }

  static String removeAttributesPrefix(String currentPath) {
    return StringUtils.removeStart(
            StringUtils.removeStart(currentPath, DATA_ATTRIBUTES), ".");
  }

}
