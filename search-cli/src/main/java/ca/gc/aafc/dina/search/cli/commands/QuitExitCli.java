package ca.gc.aafc.dina.search.cli.commands;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.shell.ExitRequest;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.commands.Quit;
import org.springframework.stereotype.Component;

import ca.gc.aafc.dina.search.cli.indexing.ElasticSearchDocumentIndexer;

@Component
@ShellComponent
public class QuitExitCli implements Quit.Command {

  private final ElasticSearchDocumentIndexer indexer;

  @Autowired
  private ApplicationContext appContext;

  public QuitExitCli(ElasticSearchDocumentIndexer indexer) {
    this.indexer = indexer;
  }

  @ShellMethod(value = "Exit/Quit the cli", key = { "quit", "exit" })
  public void quit() {
    indexer.releaseResources();
    SpringApplication.exit(appContext, () -> 0);
    throw new ExitRequest(0);
  }
}
