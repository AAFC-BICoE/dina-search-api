package ca.gc.aafc.dina.search.cli.http;

import ca.gc.aafc.dina.search.cli.config.EndpointDescriptor;
import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;

public interface DinaApiAccess {

  String getFromApi(EndpointDescriptor endpointDescriptor, String objectId) throws SearchApiException;

}
