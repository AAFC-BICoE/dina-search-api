package ca.gc.aafc.dina.search.cli.config;

import org.apache.commons.lang3.BooleanUtils;

/**
 * Contains information about how to reach a specific resource API
 * @param type the json:api type
 * @param url the url to reach the resource's API
 */
public record ApiResourceDescriptor(String type, String url, Boolean enabled) {

  public boolean isEnabled(boolean defaultValue) {
    return BooleanUtils.toBooleanDefaultIfNull(enabled, defaultValue);
  }
}
