package ca.gc.aafc.dina.search.cli.exceptions;

public class SearchApiException extends Exception {

  private static final long serialVersionUID = 987750562339932511L;

  public SearchApiException(String message) {
    super(message);
  }

  public SearchApiException(String message, Throwable cause) {
    super(message, cause);
  }

  public SearchApiException(Throwable cause) {
    super(cause);
  }
}
