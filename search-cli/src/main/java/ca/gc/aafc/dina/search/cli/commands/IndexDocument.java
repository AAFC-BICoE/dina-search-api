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
public class IndexDocument {

  private final OpenIDHttpClient aClient;
  private final ServiceEndpointProperties svcEndpointProps;
  private final IndexableDocumentHandler indexableDocumentHandler;
  private final Indexer indexer;

  public IndexDocument(OpenIDHttpClient aClient, ServiceEndpointProperties svcEndpointProps,
              IndexableDocumentHandler indexableDocumentHandler,
              Indexer indexer) {

    this.aClient = aClient;
    this.svcEndpointProps = svcEndpointProps;
    this.indexableDocumentHandler = indexableDocumentHandler;
    this.indexer = indexer;
  }

  @ShellMethod(value = "Index a document into elasticsearch", key = "index-document")
  public String indexDocument(
                  @ShellOption(help = "Document type", value = { "-t", "--type" }) String type,
                  @ShellOption(help = "Unique object identifier", value = { "-i", "--documentId" }) String documentId) {

    String msg = null;
    if (!svcEndpointProps.getEndpoints().containsKey(type)) {
      msg = "Unsupported endpoint type:" + type;
      log.error(msg);
      return msg;
    }

    try {

      // Step #1: get the document
      log.info("Retrieve document id:{}", documentId);
      msg = aClient.getDataFromUrl(svcEndpointProps.getEndpoints().get(type), documentId);

      // Step #2: Assemble the document
      log.info("Assemble document id:{}", documentId);
      msg = indexableDocumentHandler.assembleDocument(msg);

      // Step #3: Index the document into elasticsearch
      log.info("Sending document id:{} to indexer", documentId);
      indexer.indexDocument(msg);

    } catch (SearchApiException sapiEx) {
      log.error("Error during operation execution", sapiEx);
    }

    return msg;
  }
}
