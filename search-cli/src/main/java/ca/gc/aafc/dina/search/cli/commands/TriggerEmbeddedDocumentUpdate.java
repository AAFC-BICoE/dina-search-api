package ca.gc.aafc.dina.search.cli.commands;

import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;
import ca.gc.aafc.dina.search.cli.indexing.DocumentManager;
import lombok.extern.log4j.Log4j2;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@ShellComponent
public class TriggerEmbeddedDocumentUpdate {

  private final DocumentManager documentManager;

  public TriggerEmbeddedDocumentUpdate(DocumentManager documentManager) {
    this.documentManager = documentManager;
  }

  @ShellMethod(value = "Trigger embedded document processing", key = "trigger-embedded-update")
  public void triggerEmbeddedDocumentProcessing(
                  @ShellOption(help = "Document type", value = { "-t", "--type" }) String type,
                  @ShellOption(help = "Unique object identifier", value = { "-i", "--documentId" }) String documentId) {

    try {
      documentManager.processEmbeddedDocument(type, documentId);
    } catch (SearchApiException e) {
      log.error("Processing error: ", e);
    }
  }
}
