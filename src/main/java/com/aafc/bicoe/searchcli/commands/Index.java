package com.aafc.bicoe.searchcli.commands;

import java.util.HashMap;
import java.util.Map;

import com.aafc.bicoe.searchcli.http.HttpClient;
import com.aafc.bicoe.searchcli.jsonapi.JsonSpecUtils;
import com.aafc.bicoe.searchcli.jsonapi.model.DinaType;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@ShellComponent
public class Index {

    public static final String PERSON_INCLUDED_RELATIONSHIPS_STRING = "organizations";
    public static final String METADATA_INCLUDED_RELATIONSHIPS_STRING = "managedAttributeMap,derivatives,acMetadataCreator,acDerivedFrom,dcCreator"; // TODO: Problem with managedAttribute

    private JsonSpecUtils jsonSpec;
    private HttpClient aClient;
    private Map<String, String> supportedDataTypes;

    public Index(@Autowired JsonSpecUtils jsonSpec, @Autowired HttpClient aClient) {

        this.jsonSpec = jsonSpec;
        this.aClient = aClient;

        supportedDataTypes = new HashMap<>();

    }

    @ShellMethod(value = "Index a document", key = "index-doc")
    public String indexDocument(
            @ShellOption(help = "Supported types:metadata, organization, person", value = { "-t" }) String objectType,
            @ShellOption(help = "Unique object identifier", value = { "-i"}) String objectId) {

        // Based on the type call the proper method...
        //
        try {

            String rawPayload = null;
            switch (DinaType.valueOf(objectType.toUpperCase())) {

                case METADATA:
                    rawPayload = aClient.getMetadata(objectId, METADATA_INCLUDED_RELATIONSHIPS_STRING);                
                break;

                case ORGANIZATION:
                    rawPayload = aClient.getOrganization(objectId, null);
                break;

                case PERSON:
                    rawPayload = aClient.getPerson(objectId, PERSON_INCLUDED_RELATIONSHIPS_STRING);
                break;
            }

            return jsonSpec.createPublishableObject(rawPayload, supportedDataTypes);

        } catch (IllegalArgumentException argEx) {
            log.error("Please provide valid arguments");
            throw argEx;
        }
    }
}
