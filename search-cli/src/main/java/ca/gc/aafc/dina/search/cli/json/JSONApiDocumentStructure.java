package ca.gc.aafc.dina.search.cli.json;

import com.fasterxml.jackson.core.JsonPointer;

/**
 * Collections of constants and utilities for JSON:API related code.
 */
public final class JSONApiDocumentStructure {

  // Utility class
  private JSONApiDocumentStructure() {
  }

  public static final String DATA = "data";
  public static final String INCLUDED = "included";
  public static final String META = "meta";

  public static final String ATTRIBUTES = "attributes";
  public static final String ID = "id";
  public static final String TYPE = "type";

  // JSON Pointer version
  public static final JsonPointer DATA_PTR = JsonPointer.valueOf("/" + DATA);
  public static final JsonPointer INCLUDED_PTR = JsonPointer.valueOf("/" + INCLUDED);
  public static final JsonPointer META_PTR = JsonPointer.valueOf("/" + META);
  public static final String ATTRIBUTES_PTR = DATA_PTR + "/" + ATTRIBUTES;


}
