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
public class GetMetadata {

    public static final String INCLUDED_RELATIONSHIPS_STRING = "managedAttributeMap,derivatives,acMetadataCreator,acDerivedFrom,dcCreator"; // TODO: Problem with managedAttribute

    private JsonSpecUtils jsonSpec;
    private HttpClient aClient;
    private Map<String, String> supportedDataTypes;
    
    public GetMetadata(@Autowired JsonSpecUtils jsonSpec, @Autowired HttpClient aClient) {

        this.jsonSpec = jsonSpec;
        this.aClient = aClient;

        supportedDataTypes = new HashMap<>();
        supportedDataTypes.put("person", "Person");
        supportedDataTypes.put("metadata", "Metadata");
        supportedDataTypes.put("managed-attribute-map", "ManagedAttributeMap");

    }


    @ShellMethod(value = "Get Metadata", key = "get-metadata")
    public String getMetaData(
            @ShellOption(defaultValue = "_NONE_", value = { "-i", "--metadataId" }) String metadataId) {

        String rawPayload = null;
        if (!metadataId.equals("_NONE_")) {
            rawPayload = aClient.getMetadata(metadataId, INCLUDED_RELATIONSHIPS_STRING);
        } else {
            rawPayload = aClient.getMetadata(null, INCLUDED_RELATIONSHIPS_STRING);
        }

        return jsonSpec.createPublishableObject(rawPayload, supportedDataTypes);

    }

}
