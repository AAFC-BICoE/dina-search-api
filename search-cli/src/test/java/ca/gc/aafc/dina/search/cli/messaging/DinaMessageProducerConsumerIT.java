package ca.gc.aafc.dina.search.cli.messaging;

import ca.gc.aafc.dina.messaging.config.RabbitMQQueueProperties;
import ca.gc.aafc.dina.search.cli.commands.messaging.DocumentProcessor;
import ca.gc.aafc.dina.search.cli.config.MessageProcessorTestConfiguration;
import ca.gc.aafc.dina.search.cli.containers.DinaRabbitMQContainer;
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
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;

import javax.inject.Named;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
    properties = {
        "spring.shell.interactive.enabled=false",
        "dina.messaging.isProducer=true",
        "dina.messaging.isConsumer=true",
        "rabbitmq.queue=dina.search.queue"
    })
@EnableAutoConfiguration(exclude={DataSourceAutoConfiguration.class})
@Import(MessageProcessorTestConfiguration.class)
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
  private LatchBasedMessageProcessor latchBasedMessageProcessor;

  @Autowired
  @Named("searchQueueProperties")
  private RabbitMQQueueProperties rabbitMQSearchProperties;

  @Autowired
  private RabbitTemplate rabbitTemplate;

  @Autowired
  private DocumentOperationNotificationConsumer documentConsumer;

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
      "material-sample", LatchBasedMessageProcessor.INVALID_DOC_ID, DocumentOperationType.ADD);
    messageProducer.send(expected);
    //give5SecondsForMessageDelivery();
    latchBasedMessageProcessor.waitForMessage();

    rabbitTemplate.setExchange("");
    rabbitTemplate.setReceiveTimeout(1000);
    Message message = rabbitTemplate.receive("dina.search.queue.dlq");

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
   *
   */
  private void validateMessageTransferAndProcessingByConsumer(DocumentOperationNotification docNotification) throws InterruptedException {
    messageProducer.send(docNotification);
    assertResult(docNotification, latchBasedMessageProcessor.waitForMessage());
  }

  private void assertResult(DocumentOperationNotification docOperation, DocumentOperationNotification fromConsumer) {
    Assertions.assertEquals(docOperation.isDryRun(), fromConsumer.isDryRun());
    Assertions.assertEquals(docOperation.getOperationType(), fromConsumer.getOperationType());
    Assertions.assertEquals(docOperation.getDocumentId(), fromConsumer.getDocumentId());
    Assertions.assertEquals(docOperation.getDocumentType(), fromConsumer.getDocumentType());
  }

}
