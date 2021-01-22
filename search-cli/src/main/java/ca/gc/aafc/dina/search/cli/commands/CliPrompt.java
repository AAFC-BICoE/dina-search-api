package ca.gc.aafc.dina.search.cli.commands;

import org.jline.utils.AttributedString;
import org.springframework.shell.jline.PromptProvider;
import org.springframework.stereotype.Component;
import org.jline.utils.AttributedStyle;

@Component
public class CliPrompt implements PromptProvider {

  @Override
  public AttributedString getPrompt() {
    return new AttributedString("search-cli:>", AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW));
  }
}
