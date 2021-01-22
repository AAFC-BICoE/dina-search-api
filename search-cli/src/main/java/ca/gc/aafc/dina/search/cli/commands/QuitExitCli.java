package ca.gc.aafc.dina.search.cli.commands;

import org.springframework.shell.ExitRequest;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.commands.Quit;
import org.springframework.stereotype.Component;

@Component
@ShellComponent
public class QuitExitCli implements Quit.Command {

  @ShellMethod(value = "Exit/Quit the cli", key = { "quit", "exit" })
  public void quit() {
    throw new ExitRequest();
  }
}
