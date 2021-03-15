package ca.gc.aafc.dina.search.cli.exceptions;

public class SearchApiNotFoundException extends SearchApiException {

  private static final long serialVersionUID = 987750562339932511L;

  public SearchApiNotFoundException(String message) {
    super(message);
  }

  public SearchApiNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }

  public SearchApiNotFoundException(Throwable cause) {
    super(cause);
  }
}
