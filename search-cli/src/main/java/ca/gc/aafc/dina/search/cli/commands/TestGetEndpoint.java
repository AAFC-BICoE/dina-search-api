package ca.gc.aafc.dina.search.cli.commands;

import ca.gc.aafc.dina.search.cli.config.IndexSettingDescriptor;
import lombok.extern.log4j.Log4j2;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

import ca.gc.aafc.dina.search.cli.config.ServiceEndpointProperties;
import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;
import ca.gc.aafc.dina.search.cli.http.OpenIDHttpClient;

@Log4j2
@Component
public class TestGetEndpoint {

  private final OpenIDHttpClient aClient;
  private final ServiceEndpointProperties svcEndpointProps;

  public TestGetEndpoint(OpenIDHttpClient aClient, ServiceEndpointProperties svcEndpointProps) {
    this.aClient = aClient;
    this.svcEndpointProps = svcEndpointProps;
  }

  @Command(name = "test-get-endpoint", description = "Test Get Endpoint")
  public String testGetEndpoint(@Option(shortName = 't', longName = "type", description = "Document type") String type) {

    String msg = null;
    if (!svcEndpointProps.isTypeSupportedForEndpointDescriptor(type)) {
      msg = "Unsupported endpoint type:" + type;
      log.error(msg);
      return msg;
    }

    try {
      IndexSettingDescriptor indexSettingDescriptor = svcEndpointProps.getIndexSettingDescriptorForType(type);
      msg = aClient.getDataFromUrl(svcEndpointProps.getApiResourceDescriptorForType(type),
          indexSettingDescriptor.relationships(), indexSettingDescriptor.optionalFields());
    } catch (SearchApiException sapiEx) {
      log.error("Error during operation execution", sapiEx);
    }

    return msg;
  }
}
