package ca.gc.aafc.dina.search.cli.messaging;

import ca.gc.aafc.dina.messaging.message.DocumentOperationNotification;
import ca.gc.aafc.dina.search.messaging.consumer.IMessageProcessor;
import lombok.extern.log4j.Log4j2;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Allows to wait until a single message is received.
 * This class is meant test a single message at the time.
 */
@Log4j2
public class LatchBasedMessageProcessor implements IMessageProcessor {

  private static final int MAX_WAIT_SEC = 10;

  // Use as documentId to throw a runtime exception. Useful to test DLQ
  public static final String INVALID_DOC_ID = "Invalid";

  private CountDownLatch latch = new CountDownLatch(1);
  private DocumentOperationNotification message;

  @Override
  public void processMessage(DocumentOperationNotification docOpMessage) {
    if (docOpMessage == null) {
      log.warn("Invalid document operation message received, will not process it");
      return;
    }

    message = docOpMessage;
    latch.countDown();

    if(INVALID_DOC_ID.equals(docOpMessage.getDocumentId())) {
      throw new RuntimeException("Invalid document id");
    }
  }

  /**
   * Reset the latch.
   */
  public void resetLatch() {
    latch = new CountDownLatch(1);
  }

  /***
   * Wait until we receive the message and reset the latch (so another message can be received).
   * @return
   */
  public DocumentOperationNotification waitForMessage() throws InterruptedException {
    if(latch.await(MAX_WAIT_SEC, TimeUnit.SECONDS)) {
      resetLatch();
      return message;
    }
    log.warn("latch timed-out");
    return null;
  }

}
