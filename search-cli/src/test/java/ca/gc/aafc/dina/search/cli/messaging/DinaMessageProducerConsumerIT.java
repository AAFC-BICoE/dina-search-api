package ca.gc.aafc.dina.search.cli.messaging;

import ca.gc.aafc.dina.search.cli.commands.messaging.DocumentProcessor;
import ca.gc.aafc.dina.search.cli.containers.DinaRabbitMQContainer;
import ca.gc.aafc.dina.search.common.config.RabbitMQConsumerConfiguration;
import ca.gc.aafc.dina.search.messaging.consumer.DocumentOperationNotificationConsumer;
import ca.gc.aafc.dina.search.messaging.producer.MessageProducer;
import ca.gc.aafc.dina.search.messaging.types.DocumentOperationNotification;
import ca.gc.aafc.dina.search.messaging.types.DocumentOperationType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

@SpringBootTest(
  properties = {
    "spring.shell.interactive.enabled=false",
      "dina.messaging.isProducer=true",
      "dina.messaging.isConsumer=true",
    "rabbitmq.queue=dina.search.queue",
    "rabbitmq.exchange=dina.search.exchange",
    "rabbitmq.routingkey=dina.search.routingkey",
  })
@EnableAutoConfiguration(exclude={DataSourceAutoConfiguration.class})
@EnableRabbit
class DinaMessageProducerConsumerIT {

  @Container
  private static final RabbitMQContainer rabbitMQContainer = new DinaRabbitMQContainer();

  @DynamicPropertySource
  static void registerRabbitMQProperties(DynamicPropertyRegistry registry) {
    registry.add("rabbitmq.host", rabbitMQContainer::getHost);
    registry.add("rabbitmq.port", rabbitMQContainer::getAmqpPort);
    registry.add("rabbitmq.username", rabbitMQContainer::getAdminUsername);
    registry.add("rabbitmq.password", rabbitMQContainer::getAdminPassword);
  }

  @Autowired
  private MessageProducer messageProducer;

  @Autowired
  private RabbitMQConsumerConfiguration rabbitMQConsumerConfiguration;

  @Autowired
  private RabbitTemplate rabbitTemplate;

  @SpyBean
  @Autowired
  private DocumentOperationNotificationConsumer documentConsumer;

  @SpyBean
  @Autowired
  private DocumentProcessor documentProcessor;

  @BeforeAll
  static void beforeAll() {
    rabbitMQContainer.start();
    assertTrue(rabbitMQContainer.isRunning());
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
  void onMessageThatThrowsException_messageSentInDLQ() {
    DocumentOperationNotification expected = new DocumentOperationNotification(false,
      // dryRun = false will fail to connect and throw the needed exception
      "material-sample", "Invalid", DocumentOperationType.ADD);
    messageProducer.send(expected);
    give5SecondsForMessageDelivery();

    rabbitTemplate.setExchange(rabbitMQConsumerConfiguration.getDeadLetterExchangeName());
    Message message = rabbitTemplate.receive(rabbitMQConsumerConfiguration.getDeadLetterQueueName());

    if (message == null) {
      Assertions.fail("a message should of been in the que");
    }

    DocumentOperationNotification result = new ObjectMapper().readValue(
      new String(message.getBody()),
      DocumentOperationNotification.class);
    Assertions.assertEquals(expected.getDocumentId(), result.getDocumentId());
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

}
