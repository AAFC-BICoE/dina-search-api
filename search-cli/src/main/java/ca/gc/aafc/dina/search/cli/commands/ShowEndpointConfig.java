package ca.gc.aafc.dina.search.cli.commands;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.stereotype.Component;

import ca.gc.aafc.dina.search.cli.config.ServiceEndpointProperties;

@Component
@ShellComponent
public class ShowEndpointConfig {

  private final ServiceEndpointProperties svcEndpointProps;

  public ShowEndpointConfig(ServiceEndpointProperties svcEndpointProps) {
    this.svcEndpointProps = svcEndpointProps;
  }

  @ShellMethod(value = "Show service endpoint configuration", key = "show-endpoints")
  public String showEndpointConfig() {

    Map<String, String> descriptorMap = 
          svcEndpointProps.getEndpoints().entrySet()
            .stream()
            .filter(e ->  !"endpointDescriptor".equalsIgnoreCase(e.getKey()))
            .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().getTargetUrl()));

    StringBuilder aBuilder = new StringBuilder();
    aBuilder.append(System.lineSeparator());
    aBuilder.append("****** Endpoints Configuration ******"  + System.lineSeparator());
    descriptorMap.entrySet().forEach(e-> aBuilder.append(e.getKey() + "=" + e.getValue() + System.lineSeparator()));
    aBuilder.append("*************************************"  + System.lineSeparator());

    return aBuilder.toString();
  }
}
