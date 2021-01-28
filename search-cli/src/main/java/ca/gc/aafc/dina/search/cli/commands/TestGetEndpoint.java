package ca.gc.aafc.dina.search.cli.commands;

import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import org.springframework.stereotype.Component;

import ca.gc.aafc.dina.search.cli.config.ServiceEndpointProperties;
import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;
import ca.gc.aafc.dina.search.cli.http.HttpClient;

@Component
@ShellComponent
public class TestGetEndpoint {

    private HttpClient aClient;
    private final ServiceEndpointProperties svcEndpointProps;
    
    public TestGetEndpoint(HttpClient aClient, ServiceEndpointProperties svcEndpointProps) {
        this.aClient = aClient;
        this.svcEndpointProps = svcEndpointProps;
    }

    @ShellMethod(value = "Test Get Endpoint", key = "test-get-endpoint")
    public String testGetEndpoint(@ShellOption(value = { "-t", "--type" }) String type) {

      if (!svcEndpointProps.getEndpoints().containsKey(type)) {
        throw new SearchApiException("Unsupported endpoint type");        
      }

      return aClient.getDataFromUrl(svcEndpointProps.getEndpoints().get(type));
    }
}
