package com.aafc.bicoe.searchcli.commands;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.aafc.bicoe.searchcli.http.HttpClient;
import com.aafc.bicoe.searchcli.jsonapi.JsonSpecUtils;
import com.aafc.bicoe.searchcli.jsonapi.model.DinaType;
import com.aafc.bicoe.searchcli.services.IIndexer;

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
    public static final String METADATA_INCLUDED_RELATIONSHIPS_STRING = "managedAttributeMap,derivatives,acMetadataCreator,acDerivedFrom,dcCreator"; // TODO:
                                                                                                                                                     // Problem
                                                                                                                                                     // with
                                                                                                                                                     // managedAttribute

    private JsonSpecUtils jsonSpec;
    private HttpClient aClient;
    private Map<String, String> supportedDataTypes;

    private IIndexer indexerService;

    public Index(@Autowired JsonSpecUtils jsonSpec, @Autowired HttpClient aClient, @Autowired IIndexer indexerService) {

        this.jsonSpec = jsonSpec;
        this.aClient = aClient;
        this.indexerService = indexerService;

        supportedDataTypes = new HashMap<>();
        supportedDataTypes.put("person", "Person");
        supportedDataTypes.put("metadata", "Metadata");
        supportedDataTypes.put("managed-attribute-map", "ManagedAttributeMap");

    }

    @ShellMethod(value = "Index a document", key = "index-doc")
    public String indexDocument(@ShellOption(defaultValue = "false", value = { "--dryrun" }) boolean dryRun,
            @ShellOption(help = "Supported types:metadata, organization, person", value = { "-t" }) String objectType,
            @ShellOption(help = "Unique object identifier", value = { "-i" }) String objectId) throws IOException {

        // Based on the type call the proper method...
        //
        try {

            log.info("******  Dry run is set to {}", dryRun);

            String rawPayload = null;
            DinaType dinaType = DinaType.valueOf(objectType.toUpperCase());

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

            String aDocument = jsonSpec.createPublishableObject(rawPayload, supportedDataTypes);

            if (!dryRun) {
                indexerService.indexDocument(dinaType, aDocument);
            }

            return aDocument;

        } catch (IllegalArgumentException argEx) {
            log.error("Please provide valid arguments");
            throw argEx;
        } catch (IOException ioEx) {
            log.error("Please verified connectivity with elasticsearch");
            throw ioEx;
        }
    }
}
