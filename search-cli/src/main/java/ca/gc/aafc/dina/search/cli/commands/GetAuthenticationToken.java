package ca.gc.aafc.dina.search.cli.commands;

import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;
import ca.gc.aafc.dina.search.cli.http.OpenIDHttpClient;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ShellComponent
public class GetAuthenticationToken {

  @Autowired
  OpenIDHttpClient aClient;

  @ShellMethod(value = "Get Authentication token", key = "get-token")
  public void getAuthenticationToken() {
    try {
      aClient.getToken();
    } catch (SearchApiException sapiEx) {
      log.error("Error during authentication token registration {}", sapiEx.getMessage());
    }
  }
}