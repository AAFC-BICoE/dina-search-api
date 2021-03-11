package ca.gc.aafc.dina.search.cli.commands;

import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import org.springframework.stereotype.Component;

import ca.gc.aafc.dina.search.cli.config.ServiceEndpointProperties;
import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;
import ca.gc.aafc.dina.search.cli.http.OpenIDHttpClient;
import ca.gc.aafc.dina.search.cli.indexing.ElasticSearchDocumentIndexer;
import ca.gc.aafc.dina.search.cli.json.IndexableDocumentHandler;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
@ShellComponent
public class IndexDocument {

  private final OpenIDHttpClient aClient;
  private final ServiceEndpointProperties svcEndpointProps;
  private final IndexableDocumentHandler indexableDocumentHandler;
  private final ElasticSearchDocumentIndexer indexer;

  public IndexDocument(OpenIDHttpClient aClient, ServiceEndpointProperties svcEndpointProps,
              IndexableDocumentHandler indexableDocumentHandler,
              ElasticSearchDocumentIndexer indexer) {

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

      // Step #3: index the document into the default DINA Document indexx
      log.info("Sending document id:{} to default indexer", documentId);
      indexer.indexDocument(msg);

      // Step #4: Index the document into elasticsearch   
      if (svcEndpointProps.getEndpoints().get(type).getIndexName() != null && 
        !svcEndpointProps.getEndpoints().get(type).getIndexName().isEmpty()) {   
        log.info("Sending document id:{} to specific index {}", documentId, svcEndpointProps.getEndpoints().get(type).getIndexName());
        indexer.indexDocument(msg, svcEndpointProps.getEndpoints().get(type).getIndexName());
      }
    
    } catch (SearchApiException sapiEx) {
      log.error("Error during operation execution", sapiEx);
    }

    return msg;
  }
}
