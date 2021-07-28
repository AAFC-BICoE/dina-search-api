package ca.gc.aafc.dina.search.cli.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import java.io.IOException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;

import ca.gc.aafc.dina.search.cli.commands.messaging.DocumentProcessor;
import ca.gc.aafc.dina.search.messaging.consumer.DocumentOperationNotificationConsumer;
import ca.gc.aafc.dina.search.messaging.producer.MessageProducer;
import ca.gc.aafc.dina.search.messaging.types.DocumentOperationNotification;
import ca.gc.aafc.dina.search.messaging.types.DocumentOperationType;
import lombok.SneakyThrows;

@SpringBootTest(
  properties = {
    "spring.shell.interactive.enabled=false",
    "messaging.isProducer=true",
    "messaging.isConsumer=true",
    "rabbitmq.queue=dina.search.queue",
    "rabbitmq.exchange=dina.search.exchange",
    "rabbitmq.routingkey=dina.search.routingkey",
    "rabbitmq.username=guest",
    "rabbitmq.password=guest",
    "rabbitmq.host=localhost",
    "rabbitmq.port=15672"
  })
class DinaMessageProducerConsumerIT {

  @Autowired
  private MessageProducer messageProducer;

  @SpyBean
  @Autowired
  private DocumentOperationNotificationConsumer documentConsumer;

  @SpyBean
  @Autowired
  private DocumentProcessor documentProcessor;

  @Container
  private static RabbitMQContainer rabbitMQContainer = new DinaRabbitMQContainer();

  @BeforeAll
  static void beforeAll() {
    rabbitMQContainer.withQueue("dina.search.queue");
    rabbitMQContainer.start();

    assertEquals(5672, rabbitMQContainer.getMappedPort(5672).intValue());
    assertEquals(15672, rabbitMQContainer.getMappedPort(15672).intValue());
  }

  @AfterAll
  static void afterAll() {
    rabbitMQContainer.stop();
  }

  @SneakyThrows
  @Test
  void addDocument() {

    DocumentOperationNotification docNotification = new DocumentOperationNotification(true, "material-sample",
        "testDocumentId", DocumentOperationType.ADD);

    validateMessageTransferAndProcessingByConsumer(docNotification);
  }

  @SneakyThrows
  @Test
  void updateDocument() {

    DocumentOperationNotification docNotification = new DocumentOperationNotification(true, "material-sample",
        "testDocumentId", DocumentOperationType.UPDATE);

    validateMessageTransferAndProcessingByConsumer(docNotification);
  }

  @SneakyThrows
  @Test
  void deleteDocument() {

    DocumentOperationNotification docNotification = new DocumentOperationNotification(true, "material-sample",
        "testDocumentId", DocumentOperationType.DELETE);

    validateMessageTransferAndProcessingByConsumer(docNotification); 
  }

  private void validateMessageTransferAndProcessingByConsumer(DocumentOperationNotification docNotification) throws IOException {
    ArgumentCaptor<DocumentOperationNotification> argumentCaptor = ArgumentCaptor.forClass(DocumentOperationNotification.class);
    messageProducer.send(docNotification);

    give5SecondsForMessageDelivery();

    verify(documentConsumer).receiveMessage(argumentCaptor.capture());
    DocumentOperationNotification capturedArgument = argumentCaptor.<DocumentOperationNotification> getValue();
    assertResult(docNotification, capturedArgument);

    verify(documentProcessor).processMessage(Mockito.any(DocumentOperationNotification.class));
  }

  private void assertResult(DocumentOperationNotification docOperation, DocumentOperationNotification fromConsumer) throws java.io.IOException {
    Assertions.assertEquals(docOperation.isDryRun(), fromConsumer.isDryRun());
    Assertions.assertEquals(docOperation.getOperationType(), fromConsumer.getOperationType());
    Assertions.assertEquals(docOperation.getDocumentId(), fromConsumer.getDocumentId());
    Assertions.assertEquals(docOperation.getDocumentType(), fromConsumer.getDocumentType());
  }

  private void give5SecondsForMessageDelivery() {
    try {
      Thread.currentThread().sleep(1000 * 5);
    } catch (InterruptedException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }
}
