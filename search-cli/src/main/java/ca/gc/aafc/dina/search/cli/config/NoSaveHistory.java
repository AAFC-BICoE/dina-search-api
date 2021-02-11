package ca.gc.aafc.dina.search.cli.config;

import java.io.IOException;

import org.jline.reader.impl.history.DefaultHistory;
import org.springframework.stereotype.Component;

@Component
public class NoSaveHistory  extends DefaultHistory {
  @Override
  public void save() throws IOException {
    // Disable history log @see https://github.com/spring-projects/spring-shell/issues/194
  }
}
