package ca.gc.aafc.dina.search.messaging.consumer;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import ca.gc.aafc.dina.search.messaging.types.DocumentOperationNotification;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
@Profile("dina.search.consumer")
public class DocumentOperationNotificationConsumer {
  
  @RabbitListener(queues = "dina.search.queue")
  public void receiveMessage(final DocumentOperationNotification docOperationMessage) {
    log.info("Received message and deserialized to : {}", docOperationMessage.toString());
  }

}
