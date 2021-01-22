package ca.gc.aafc.dina.search.cli.commands;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.stereotype.Component;

import ca.gc.aafc.dina.search.cli.config.ServiceEndpointProperties;

@Component
@ShellComponent
public class ShowEndpointConfig {

  private ServiceEndpointProperties svcEndpointProps;

  public ShowEndpointConfig(@Autowired ServiceEndpointProperties svcEndpointProps) {
    this.svcEndpointProps = svcEndpointProps;
  } 

  @ShellMethod(value = "Show service endpoint configuration", key = "show-endpoints")
  public String showEndpointConfig() {

    StringBuilder aBuilder = new StringBuilder();

    aBuilder.append("****** Endpoints Configuration ******"  + System.lineSeparator());
    aBuilder.append("Organization=" + this.svcEndpointProps.getOrganizationEndpoint() + System.lineSeparator());
    aBuilder.append("Person=" + this.svcEndpointProps.getPersonEndpoint() + System.lineSeparator());
    aBuilder.append("Metadata=" + this.svcEndpointProps.getMetadataEndpoint() + System.lineSeparator());  
    aBuilder.append("*************************************"  + System.lineSeparator());

    return aBuilder.toString();
  }
}
