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
public class GetOrganization {

    private JsonSpecUtils jsonSpec;
    private HttpClient aClient;
    private Map<String, String> supportedDataTypes;
    
    public GetOrganization(@Autowired JsonSpecUtils jsonSpec, @Autowired HttpClient aClient) {

        this.jsonSpec = jsonSpec;
        this.aClient = aClient;

        supportedDataTypes = new HashMap<>();

    }

    @ShellMethod(value = "Get Organization", key = "get-organization")
    public String getOrganization(@ShellOption(defaultValue = "_NONE_", value= {"-i", "--organizationId"}) String organizationId) {

        String rawPayload = null;
        if (!organizationId.equals("_NONE_")) {
            rawPayload = aClient.getOrganization(organizationId, null);
        } else {
            rawPayload = aClient.getOrganization(null, null);
        }

        return jsonSpec.createPublishableObject(rawPayload, supportedDataTypes);

    }

}
