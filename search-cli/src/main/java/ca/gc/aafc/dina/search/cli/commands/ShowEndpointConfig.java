package ca.gc.aafc.dina.search.cli.commands;

import org.springframework.shell.core.command.annotation.Command;
import org.springframework.stereotype.Component;

import ca.gc.aafc.dina.search.cli.config.ServiceEndpointProperties;

@Component
public class ShowEndpointConfig {

  private final ServiceEndpointProperties svcEndpointProps;

  public ShowEndpointConfig(ServiceEndpointProperties svcEndpointProps) {
    this.svcEndpointProps = svcEndpointProps;
  }

  @Command(name = "show-endpoints", description = "Show service endpoint configuration")
  public String showEndpointConfig() {

    StringBuilder aBuilder = new StringBuilder();
    aBuilder.append(System.lineSeparator());
    aBuilder.append("****** Endpoints Configuration ******"  + System.lineSeparator());
    svcEndpointProps.getFilteredEndpointDescriptorStream(a -> true).
        forEach(e-> aBuilder.append(e.toString() + System.lineSeparator()));
    aBuilder.append("*************************************"  + System.lineSeparator());

    return aBuilder.toString();
  }
}
