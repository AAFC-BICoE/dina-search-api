package ca.gc.aafc.dina.search.cli.messaging;

import ca.gc.aafc.dina.messaging.message.DocumentOperationNotification;
import lombok.extern.log4j.Log4j2;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Allows to wait until a single message is received.
 * This class is meant test a single message at the time.
 */
@Log4j2
public class LatchBasedMessageProcessor implements IMessageProcessor {

  private static final int MAX_WAIT_SEC = 30;

  // Use as documentId to throw a runtime exception. Useful to test DLQ
  public static final String INVALID_DOC_ID = "Invalid";

  // use static variables. Sometimes the tests might get different instances
  private static final Map<String, CountDownLatch> LATCH_MAP = new ConcurrentHashMap<>();
  private static final Map<String, DocumentOperationNotification> RECEIVED_MESSAGES = new ConcurrentHashMap<>();

  @Override
  public void processMessage(DocumentOperationNotification docOpMessage) {
    if (docOpMessage == null) {
      log.warn("Invalid document operation message received, will not process it");
      return;
    }

    RECEIVED_MESSAGES.put(docOpMessage.getDocumentId(), docOpMessage);

    LATCH_MAP.get(docOpMessage.getDocumentId()).countDown();

    if(INVALID_DOC_ID.equals(docOpMessage.getDocumentId())) {
      throw new RuntimeException("Invalid document id");
    }
  }

  /**
   * Reset the latch.
   */
  public void registerLatchKey(String key) {
    LATCH_MAP.put(key, new CountDownLatch(1));
  }

  /***
   * Wait until we receive the message and reset the latch (so another message can be received).
   * @return
   */
  public DocumentOperationNotification waitForMessage(String key) throws InterruptedException {
    if(LATCH_MAP.get(key).await(MAX_WAIT_SEC, TimeUnit.SECONDS)) {
      return RECEIVED_MESSAGES.get(key);
    }
    log.warn("latch timed-out for key {}", key);
    return null;
  }

}
