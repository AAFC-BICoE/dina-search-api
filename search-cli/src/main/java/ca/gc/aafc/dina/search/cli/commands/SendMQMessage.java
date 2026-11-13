package ca.gc.aafc.dina.search.cli.commands;

import ca.gc.aafc.dina.messaging.message.DocumentOperationNotification;
import ca.gc.aafc.dina.messaging.message.DocumentOperationType;
import ca.gc.aafc.dina.messaging.producer.DocumentOperationNotificationMessageProducer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "messaging", name = "isProducer", havingValue = "true")
public class SendMQMessage {

  private final DocumentOperationNotificationMessageProducer messageProducer;

  public SendMQMessage(DocumentOperationNotificationMessageProducer messageProducer) {
    this.messageProducer = messageProducer;
  }

  @Command(name = "send-message", description = "Send Message through RabbitMQ")
  public void sendMessage(
    @Option(longName = "druRun", description = "dryRun", defaultValue = "false") boolean dryRun,
    @Option(shortName = 't', longName = "type", description = "Document type (metadata, person...)") String type,
    @Option(shortName = 'i', longName = "documentId", description = "Unique object identifier") String documentId,
    @Option(shortName = 'o', longName = "operation", description = "Document operation (add/update/delete)") String operation) {

    DocumentOperationType docOperationType = DocumentOperationType.valueOf(operation.toUpperCase());

    DocumentOperationNotification docNotification = DocumentOperationNotification.builder()
        .dryRun(dryRun)
        .documentType(type)
        .documentId(documentId)
        .operationType(docOperationType)
        .build();
    messageProducer.send(docNotification);
  }
}
