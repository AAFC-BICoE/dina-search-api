package com.aafc.bicoe.searchcli.commands;

import java.util.HashMap;
import java.util.Map;

import com.aafc.bicoe.searchcli.http.HttpClient;
import com.aafc.bicoe.searchcli.jsonapi.JsonSpecUtils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import org.springframework.stereotype.Component;


@Component
@ShellComponent
public class GetPerson {

    public static final String INCLUDED_RELATIONSHIPS_STRING = "organizations";

    private JsonSpecUtils jsonSpec;
    private HttpClient aClient;
    private Map<String, String> supportedDataTypes;
    
    public GetPerson(@Autowired JsonSpecUtils jsonSpec, @Autowired HttpClient aClient) {

        this.jsonSpec = jsonSpec;
        this.aClient = aClient;

        supportedDataTypes = new HashMap<>();

    }

    @ShellMethod(value = "Get Person", key = "get-person")
    public String getPerson(@ShellOption(defaultValue = "_NONE_", value= {"-i", "--personId"}) String personId) {

        String rawPayload = null;
        if (!personId.equals("_NONE_")) {
            rawPayload = aClient.getPerson(personId, INCLUDED_RELATIONSHIPS_STRING);
        } else {
            rawPayload = aClient.getPerson(null, INCLUDED_RELATIONSHIPS_STRING);
        }

        return jsonSpec.createPublishableObject(rawPayload, supportedDataTypes);

    }

}
