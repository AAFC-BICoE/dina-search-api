package ca.gc.aafc.dina.search.cli.commands;

import ca.gc.aafc.dina.search.cli.http.HttpClient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.stereotype.Component;


@Component
@ShellComponent
public class GetAuthenticationToken {
    
    @Autowired
    HttpClient aClient;

    @ShellMethod(value = "Get Authentication token", key = "get-token")
    public String getAuthenticationToken() {
        return aClient.getToken();        
    }
}