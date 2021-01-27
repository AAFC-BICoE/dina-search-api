package ca.gc.aafc.dina.search.cli.commands;

import java.util.HashMap;
import java.util.Map;

import ca.gc.aafc.dina.search.cli.config.ServiceEndpointProperties;
import ca.gc.aafc.dina.search.cli.http.HttpClient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import org.springframework.stereotype.Component;

@Component
@ShellComponent
public class GetMetadata {

    public static final String INCLUDED_RELATIONSHIPS_STRING = "managedAttributeMap,derivatives,acMetadataCreator,acDerivedFrom,dcCreator"; // TODO: Problem with managedAttribute
    private HttpClient aClient;
    private Map<String, String> supportedDataTypes;
    private final ServiceEndpointProperties svcEndpointProps;
    
    public GetMetadata(@Autowired HttpClient aClient, ServiceEndpointProperties svcEndpointProps) {

        this.aClient = aClient;
        this.svcEndpointProps = svcEndpointProps;

        supportedDataTypes = new HashMap<>();
        supportedDataTypes.put("person", "Person");
        supportedDataTypes.put("metadata", "Metadata");
        supportedDataTypes.put("managed-attribute-map", "ManagedAttributeMap");

    }

    @ShellMethod(value = "Get Metadata", key = "get-metadata")
    public String getMetaData(
            @ShellOption(value = { "-i", "--metadataId" }) String metadataId) {

        return aClient.getMetadata(svcEndpointProps.getEndpoints().get("metadata") ,metadataId, INCLUDED_RELATIONSHIPS_STRING);

    }

}
