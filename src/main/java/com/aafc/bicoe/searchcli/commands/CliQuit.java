package com.aafc.bicoe.searchcli.commands;

import com.aafc.bicoe.searchcli.services.IIndexer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.commands.Quit;
import org.springframework.stereotype.Component;


@Component
@ShellComponent
public class CliQuit implements Quit.Command {

    public static final String INCLUDED_RELATIONSHIPS_STRING = "organizations";


    @Autowired
    private IIndexer indexerService;


    @ShellMethod(value = "Exit/Quit the cli", key = {"quit", "exit"})
    public void quit() {

        // Need to release resorces...
        indexerService.releaseResources();
        System.exit(0);
    }

}
