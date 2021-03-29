package ca.gc.aafc.dina.search.cli.commands;

import org.apache.commons.lang3.StringUtils;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import org.springframework.stereotype.Component;

import ca.gc.aafc.dina.search.cli.config.EndpointDescriptor;
import ca.gc.aafc.dina.search.cli.config.ServiceEndpointProperties;
import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;
import ca.gc.aafc.dina.search.cli.indexing.ElasticSearchDocumentIndexer;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
@ShellComponent
public class DeleteDocument {

  private final ServiceEndpointProperties svcEndpointProps;
  private final ElasticSearchDocumentIndexer indexer;

  public DeleteDocument(ServiceEndpointProperties svcEndpointProps,
              ElasticSearchDocumentIndexer indexer) {

    this.svcEndpointProps = svcEndpointProps;
    this.indexer = indexer;
  }

  @ShellMethod(value = "Delete a document from elasticsearch", key = "delete-document")
  public String deleteDocument(
                  @ShellOption(help = "Document type", value = { "-t", "--type" }) String type,
                  @ShellOption(help = "Unique object identifier", value = { "-i", "--documentId" }) String documentId) {

    String msg = null;
    if (!svcEndpointProps.getEndpoints().containsKey(type)) {
      msg = "Unsupported endpoint type:" + type;
      log.error(msg);
      return msg;
    }

    try {

      EndpointDescriptor endpointDescriptor = svcEndpointProps.getEndpoints().get(type);

      // Step #1: Delete the document from the default DINA Document index
      log.info("Delete document id:{} to default indexer", documentId);
      indexer.deleteDocument(documentId);

      // Step #2: Delete the document from elasticsearch
      if (StringUtils.isNotBlank(endpointDescriptor.getIndexName())) {
        log.info("Deleting document id:{} from specific index {}", documentId, endpointDescriptor.getIndexName());
        indexer.deleteDocument(documentId, endpointDescriptor.getIndexName());
      }
    
    } catch (SearchApiException sapiEx) {
      log.error("Error during operation execution", sapiEx);
    }

    return msg;
  }
}
