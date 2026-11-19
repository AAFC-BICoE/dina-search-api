package ca.gc.aafc.dina.search.cli.messaging;

import java.io.IOException;

import com.rabbitmq.client.GetResponse;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Replays messages from a dead-letter queue to a target queue. */
@Service
@ConditionalOnProperty(prefix = "dina.messaging", name = "isConsumer", havingValue = "true")
public class DeadLetterQueueReplayer {

  private final RabbitTemplate rabbitTemplate;

  public DeadLetterQueueReplayer(RabbitTemplate rabbitTemplate) {
    this.rabbitTemplate = rabbitTemplate;
  }

  /**
   * Moves messages from the dead-letter queue to the target queue.
   *
   * @param dlQueue the dead-letter queue to drain
   * @param queue the queue to publish messages to
   * @return the number of messages moved
   */
  public int replay(String dlQueue, String queue) {
    return rabbitTemplate.execute(channel -> {
      channel.queueDeclarePassive(queue);
      int messagesToReplay = channel.queueDeclarePassive(dlQueue).getMessageCount();

      int replayedMessages = 0;
      for (int i = 0; i < messagesToReplay; i++) {
        GetResponse deadLetter = channel.basicGet(dlQueue, false);
        if (deadLetter == null) {
          break;
        }

        try {
          channel.basicPublish("", queue, deadLetter.getProps(), deadLetter.getBody());
          channel.basicAck(deadLetter.getEnvelope().getDeliveryTag(), false);
          replayedMessages++;
        } catch (IOException publishException) {
          throw publishException;
        }
      }
      return replayedMessages;
    });
  }
}
