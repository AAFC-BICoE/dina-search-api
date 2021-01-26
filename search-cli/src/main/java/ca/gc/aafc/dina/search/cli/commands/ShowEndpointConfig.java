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
    aBuilder.append(System.lineSeparator());
    aBuilder.append("****** Endpoints Configuration ******"  + System.lineSeparator());
    svcEndpointProps.getEndpoints().entrySet().stream().forEach(e-> aBuilder.append(e.getKey() + "=" + e.getValue() + System.lineSeparator()));
    aBuilder.append("*************************************"  + System.lineSeparator());

    return aBuilder.toString();
  }
}
