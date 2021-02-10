package ca.gc.aafc.dina.search.cli.commands;

import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import org.springframework.stereotype.Component;

import ca.gc.aafc.dina.search.cli.config.ServiceEndpointProperties;
import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;
import ca.gc.aafc.dina.search.cli.http.OpenIDHttpClient;
import ca.gc.aafc.dina.search.cli.json.IndexableDocumentHandler;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
@ShellComponent
public class GetDocument {

  private final OpenIDHttpClient aClient;
  private final ServiceEndpointProperties svcEndpointProps;
  private final IndexableDocumentHandler indexableDocumentHandler;

  public GetDocument(OpenIDHttpClient aClient, ServiceEndpointProperties svcEndpointProps,
              IndexableDocumentHandler indexableDocumentHandler) {

    this.aClient = aClient;
    this.svcEndpointProps = svcEndpointProps;
    this.indexableDocumentHandler = indexableDocumentHandler;
  }

  @ShellMethod(value = "Get Document from a specified endpoint", key = "get-document")
  public String getDocument(
                  @ShellOption(help = "Document type", value = { "-t", "--type" }) String type,
                  @ShellOption(help = "Unique object identifier", value = { "-i", "--documentId" }) String documentId,
                  @ShellOption(defaultValue = "false", value = "--merge" ) boolean merge) {

    String msg = null;
    if (!svcEndpointProps.getEndpoints().containsKey(type)) {
      msg = "Unsupported endpoint type:" + type;
      log.error(msg);
      return msg;
    }

    try {
      msg = aClient.getDataFromUrl(svcEndpointProps.getEndpoints().get(type), documentId);

      if (merge) {
        msg = indexableDocumentHandler.assembleDocument(msg);
      }
    } catch (SearchApiException sapiEx) {
      log.error("Error during operation execution", sapiEx);
    }

    return msg;
  }
}
