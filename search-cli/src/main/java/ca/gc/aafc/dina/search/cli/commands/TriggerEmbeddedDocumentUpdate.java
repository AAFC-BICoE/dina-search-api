package ca.gc.aafc.dina.search.cli.commands;

import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;
import ca.gc.aafc.dina.search.cli.indexing.DocumentManager;
import lombok.extern.log4j.Log4j2;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

@Log4j2
@Component
public class TriggerEmbeddedDocumentUpdate {

  private final DocumentManager documentManager;

  public TriggerEmbeddedDocumentUpdate(DocumentManager documentManager) {
    this.documentManager = documentManager;
  }

  @Command(name = "trigger-embedded-update", description = "Trigger embedded document processing")
  public void triggerEmbeddedDocumentProcessing(
                  @Option(shortName = 't', longName = "type", description = "Document type") String type,
                  @Option(shortName = 'i', longName = "documentId", description = "Unique object identifier") String documentId) {

    try {
      documentManager.processEmbeddedDocument(type, documentId);
    } catch (SearchApiException e) {
      log.error("Processing error: ", e);
    }
  }
}
