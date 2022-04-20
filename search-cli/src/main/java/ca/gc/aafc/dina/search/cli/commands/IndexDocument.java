package ca.gc.aafc.dina.search.cli.commands;

import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;
import lombok.extern.log4j.Log4j2;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import org.springframework.stereotype.Component;

import ca.gc.aafc.dina.search.cli.commands.messaging.DocumentProcessor;

@Log4j2
@Component
@ShellComponent
public class IndexDocument {

  private final DocumentProcessor documentProcessor;

  public IndexDocument(DocumentProcessor documentProcessor) {
    this.documentProcessor = documentProcessor;
  }

  @ShellMethod(value = "Index a document into elasticsearch", key = "index-document")
  public void indexDocument(
                  @ShellOption(help = "Document type", value = { "-t", "--type" }) String type,
                  @ShellOption(help = "Unique object identifier", value = { "-i", "--documentId" }) String documentId) {

    try {
      documentProcessor.indexDocument(type, documentId);
    } catch (SearchApiException e) {
      log.error("Indexing error: ", e);
    }
  }
}
