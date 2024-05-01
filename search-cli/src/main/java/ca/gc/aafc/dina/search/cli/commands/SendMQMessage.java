package ca.gc.aafc.dina.search.cli.commands;

import ca.gc.aafc.dina.messaging.message.DocumentOperationNotification;
import ca.gc.aafc.dina.messaging.message.DocumentOperationType;
import ca.gc.aafc.dina.messaging.producer.DocumentOperationNotificationMessageProducer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import org.springframework.stereotype.Component;

@Component
@ShellComponent
@ConditionalOnProperty(prefix = "messaging", name = "isProducer", havingValue = "true")
public class SendMQMessage {

  private final DocumentOperationNotificationMessageProducer messageProducer;

  public SendMQMessage(DocumentOperationNotificationMessageProducer messageProducer) {
    this.messageProducer = messageProducer;
  }

  @ShellMethod(value = "Send Message through RabbitMQ", key = "send-message")
  public void sendMessage(
    @ShellOption(help = "dryRun", defaultValue = "false", value = "--druRun") boolean dryRun,
    @ShellOption(help = "Document type (metadata, person...)", value = { "-t", "--type" }) String type,
    @ShellOption(help = "Unique object identifier", value = { "-i", "--documentId" }) String documentId,
    @ShellOption(help = "Document operation (add/update/delete)", value = { "-o", "--operation" }) String operation) {

    DocumentOperationType docOperationType = DocumentOperationType.valueOf(operation.toUpperCase());

    DocumentOperationNotification docNotification =
      new DocumentOperationNotification(dryRun, 
            type, 
            documentId, 
            docOperationType);

    messageProducer.send(docNotification);

  }
}
