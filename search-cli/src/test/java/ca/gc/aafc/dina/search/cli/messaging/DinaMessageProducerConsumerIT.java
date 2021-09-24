package ca.gc.aafc.dina.search.cli.messaging;

import ca.gc.aafc.dina.search.cli.commands.messaging.DocumentProcessor;
import ca.gc.aafc.dina.search.messaging.consumer.DocumentOperationNotificationConsumer;
import ca.gc.aafc.dina.search.messaging.producer.MessageProducer;
import ca.gc.aafc.dina.search.messaging.types.DocumentOperationNotification;
import ca.gc.aafc.dina.search.messaging.types.DocumentOperationType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

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
@EnableRabbit
class DinaMessageProducerConsumerIT {

  private static final String DINA_SEARCH_QUEUE = "dina.search.queue";

  @Autowired
  private MessageProducer messageProducer;

  @SpyBean
  @Autowired
  private DocumentOperationNotificationConsumer documentConsumer;

  @SpyBean
  @Autowired
  private DocumentProcessor documentProcessor;

  @Container
  private static final RabbitMQContainer rabbitMQContainer = new DinaRabbitMQContainer();

  @BeforeAll
  static void beforeAll() {
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
  void fatalMessage_DoesNotLoop() {
    List<DocumentOperationNotification> results = new ArrayList<>();
    Channel channel = openChannelToRabbit();
    channel.basicConsume( // Listen to the dead man's que for any messages
      "dina.search.queue.dlq",
      true,
      (s, delivery) -> results.add(new ObjectMapper().readValue(
        new String(delivery.getBody()), DocumentOperationNotification.class)),
      s -> {
      });

    DocumentOperationNotification expected = new DocumentOperationNotification(
      false, // dryRun = false will fail to connect and throw the needed exception
      "material-sample",
      "Invalid",
      DocumentOperationType.ADD);
    messageProducer.send(expected);
    give5SecondsForMessageDelivery();

    Assertions.assertEquals(1, results.size());
    Assertions.assertEquals(expected.getDocumentId(), results.get(0).getDocumentId());

    results.clear();
    channel.close();
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

  /*
   * The method is responsible for sending a message from the producer class. Validating
   * that the message consumer received the expected message.
   * 
   * Nothing is mocked, we are making use of spy objects to validate real code flow.
   *  
   */
  private void validateMessageTransferAndProcessingByConsumer(DocumentOperationNotification docNotification) {

    ArgumentCaptor<DocumentOperationNotification> argumentCaptor = ArgumentCaptor.forClass(DocumentOperationNotification.class);
    messageProducer.send(docNotification);

    give5SecondsForMessageDelivery();

    verify(documentConsumer).receiveMessage(argumentCaptor.capture());
    DocumentOperationNotification capturedArgument = argumentCaptor.getValue();
    assertResult(docNotification, capturedArgument);

    verify(documentProcessor).processMessage(Mockito.any(DocumentOperationNotification.class));
  }

  private void assertResult(DocumentOperationNotification docOperation, DocumentOperationNotification fromConsumer) {
    Assertions.assertEquals(docOperation.isDryRun(), fromConsumer.isDryRun());
    Assertions.assertEquals(docOperation.getOperationType(), fromConsumer.getOperationType());
    Assertions.assertEquals(docOperation.getDocumentId(), fromConsumer.getDocumentId());
    Assertions.assertEquals(docOperation.getDocumentType(), fromConsumer.getDocumentType());
  }

  private void give5SecondsForMessageDelivery() {
    try {
      Thread.sleep(1000 * 5);
    } catch (InterruptedException e) {
      Assertions.fail(e.getMessage());
    }
  }

  private Channel openChannelToRabbit() throws IOException, TimeoutException {
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost("localhost");
    factory.setPort(5672);
    factory.setUsername("guest");
    factory.setPassword("guest");
    factory.setVirtualHost("/");
    Connection connection = factory.newConnection();

    return connection.createChannel();
  }
}
