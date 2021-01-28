package ca.gc.aafc.dina.search.cli.commands;

import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import org.springframework.stereotype.Component;

import ca.gc.aafc.dina.search.cli.config.ServiceEndpointProperties;
import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;
import ca.gc.aafc.dina.search.cli.http.OpenIDHttpClient;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@ShellComponent
public class TestGetEndpoint {

  private OpenIDHttpClient aClient;
  private final ServiceEndpointProperties svcEndpointProps;

  public TestGetEndpoint(OpenIDHttpClient aClient, ServiceEndpointProperties svcEndpointProps) {
    this.aClient = aClient;
    this.svcEndpointProps = svcEndpointProps;
  }

  @ShellMethod(value = "Test Get Endpoint", key = "test-get-endpoint")
  public void testGetEndpoint(@ShellOption(value = { "-t", "--type" }) String type) {

    if (!svcEndpointProps.getEndpoints().containsKey(type)) {
      log.error("Unsupported endpoint type");
    }
    try {
      log.info(aClient.getDataFromUrl(svcEndpointProps.getEndpoints().get(type)));
    } catch (SearchApiException sapiEx) {
      log.error("Error during operation execution, error:{}", sapiEx.getMessage());
    }
  }
}
