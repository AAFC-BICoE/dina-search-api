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
  public String testGetEndpoint(@ShellOption(value = { "-t", "--type" }) String type) {

    String msg = null;
    if (!svcEndpointProps.getEndpoints().containsKey(type)) {
      msg = "Unsupported endpoint type:" + type;
      log.error(msg);
      return msg;
    }

    try {
      msg = aClient.getDataFromUrl(svcEndpointProps.getEndpoints().get(type));
    } catch (SearchApiException sapiEx) {
      msg = "Error during operation execution, error:" + sapiEx.getMessage();
      log.error(msg);
    }

    return msg;
  }
}
