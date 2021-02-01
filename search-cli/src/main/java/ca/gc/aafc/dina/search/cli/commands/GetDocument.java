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
public class GetDocument {

  private OpenIDHttpClient aClient;
  private final ServiceEndpointProperties svcEndpointProps;

  public GetDocument(OpenIDHttpClient aClient, ServiceEndpointProperties svcEndpointProps) {
    this.aClient = aClient;
    this.svcEndpointProps = svcEndpointProps;
  }

  @ShellMethod(value = "Get Document from a specified endpoint", key = "get-document")
  public String testGetEndpoint(
                  @ShellOption(help = "Document type", value = { "-t", "--type" }) String type,
                  @ShellOption(help = "Unique object identifier", value = { "-i", "--documentId" }) String documentId) {

    String msg = null;
    if (!svcEndpointProps.getEndpoints().containsKey(type)) {
      msg = "Unsupported endpoint type:" + type;
      log.error(msg);
      return msg;
    }

    try {
      msg = aClient.getDataFromUrl(svcEndpointProps.getEndpoints().get(type), documentId);
    } catch (SearchApiException sapiEx) {
      msg = "Error during operation execution, error:" + sapiEx.getMessage();
      log.error(msg);
    }

    return msg;
  }
}
