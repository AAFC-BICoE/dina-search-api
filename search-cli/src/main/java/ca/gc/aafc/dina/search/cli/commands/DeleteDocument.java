package ca.gc.aafc.dina.search.cli.commands;

import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import org.springframework.stereotype.Component;

import ca.gc.aafc.dina.search.cli.commands.messaging.DocumentProcessor;

@Component
@ShellComponent
public class DeleteDocument {

  private final DocumentProcessor documentProcessor;

  public DeleteDocument(DocumentProcessor documentProcessor) {
    this.documentProcessor = documentProcessor;
  }

  @ShellMethod(value = "Delete a document from elasticsearch", key = "delete-document")
  public String deleteDocument(
                  @ShellOption(help = "Document type", value = { "-t", "--type" }) String type,
                  @ShellOption(help = "Unique object identifier", value = { "-i", "--documentId" }) String documentId) {

    return documentProcessor.deleteDocument(type, documentId);

  }
}
