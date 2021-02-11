package ca.gc.aafc.dina.search.cli.commands;

import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import org.springframework.stereotype.Component;

import ca.gc.aafc.dina.search.cli.config.ServiceEndpointProperties;
import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;
import ca.gc.aafc.dina.search.cli.http.OpenIDHttpClient;
import ca.gc.aafc.dina.search.cli.indexing.Indexer;
import ca.gc.aafc.dina.search.cli.json.IndexableDocumentHandler;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
@ShellComponent
public class GetDocument {

  private final OpenIDHttpClient aClient;
  private final ServiceEndpointProperties svcEndpointProps;
  private final IndexableDocumentHandler indexableDocumentHandler;
  private final Indexer indexer;
  
  public GetDocument(OpenIDHttpClient aClient, ServiceEndpointProperties svcEndpointProps,
              IndexableDocumentHandler indexableDocumentHandler,
              Indexer indexer) {

    this.aClient = aClient;
    this.svcEndpointProps = svcEndpointProps;
    this.indexableDocumentHandler = indexableDocumentHandler;
    this.indexer = indexer;
  }

  @ShellMethod(value = "Get Document from a specified endpoint", key = "get-document")
  public String getDocument(
                  @ShellOption(help = "Document type", value = { "-t", "--type" }) String type,
                  @ShellOption(help = "Unique object identifier", value = { "-i", "--documentId" }) String documentId,
                  @ShellOption(help = "Assemble a document", defaultValue = "false", value = "--assemble" ) boolean assemble,
                  @ShellOption(help = "Assemble and Index a document", defaultValue = "false", value = "--mergeAndIndex" ) boolean assembleAndIndex) {

    String msg = null;
    if (!svcEndpointProps.getEndpoints().containsKey(type)) {
      msg = "Unsupported endpoint type:" + type;
      log.error(msg);
      return msg;
    }

    try {
      msg = aClient.getDataFromUrl(svcEndpointProps.getEndpoints().get(type), documentId);

      if (assemble || assembleAndIndex) {
        msg = indexableDocumentHandler.assembleDocument(msg);

        if (assembleAndIndex) {
          log.info("Sending document id:{} to indexer", documentId);
          indexer.indexDocument(msg);
        }
      }
    } catch (SearchApiException sapiEx) {
      log.error("Error during operation execution", sapiEx);
    }

    return msg;
  }
}
