package ca.gc.aafc.dina.search.cli.commands;

import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;
import ca.gc.aafc.dina.search.cli.indexing.DocumentManager;
import lombok.extern.log4j.Log4j2;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

@Log4j2
@Component
public class IndexDocument {

  private final DocumentManager documentManager;

  public IndexDocument(DocumentManager documentManager) {
    this.documentManager = documentManager;
  }

  @Command(name = "index-document", description = "Index a document into elasticsearch")
  public void indexDocument(
                  @Option(shortName = 't', longName = "type", description = "Document type") String type,
                  @Option(shortName = 'i', longName = "documentId", description = "Unique object identifier") String documentId) {

    try {
      documentManager.indexDocument(type, documentId);
    } catch (SearchApiException e) {
      log.error("Indexing error: ", e);
    }
  }
}
