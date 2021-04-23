package ca.gc.aafc.dina.search.cli.commands;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import org.springframework.stereotype.Component;

import ca.gc.aafc.dina.search.messaging.producer.MessageProducer;
import ca.gc.aafc.dina.search.messaging.types.DocumentOperationNotification;
import ca.gc.aafc.dina.search.messaging.types.DocumentOperationType;

@Component
@ShellComponent
@ConditionalOnProperty(prefix = "messaging", name = "producer", havingValue = "true")
public class SendMQMessage {

  private final MessageProducer messageProducer;

  public SendMQMessage(MessageProducer messageProducer) {
    this.messageProducer = messageProducer;
  }

  @ShellMethod(value = "Send Message through RabbitMQ", key = "send-message")
  public void testGetEndpoint(@ShellOption(value = { "-m", "--message" }) String message) {

    DocumentOperationNotification docNotification = 
      new DocumentOperationNotification(true, 
            message, 
            "dina-test-type", 
            DocumentOperationType.ADD);

    messageProducer.send(docNotification);

  }
}
