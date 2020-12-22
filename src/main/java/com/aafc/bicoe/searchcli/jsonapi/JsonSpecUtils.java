package com.aafc.bicoe.searchcli.jsonapi;

import java.util.Map;

import com.aafc.bicoe.searchcli.commands.GetMetadata;
import com.aafc.bicoe.searchcli.commands.GetPerson;
import com.aafc.bicoe.searchcli.http.HttpClient;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.ReadContext;
import com.jayway.jsonpath.spi.json.GsonJsonProvider;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class JsonSpecUtils {

    public static final String JSON_PATH_DATA = "$.data";
    public static final String JSON_PATH_DATA_ATTRIBUTES = "$.data.attributes";
    public static final String JSON_PATH_DATA_RELATIONSHIPS = "$.data.relationships";

    public static final String JSON_PATH_INCLUDED = "$.included";
    public static final String JSON_PATH_META = "$.meta";

    private static final String PUBLISH_DATA = "data";
    private static final String PUBLISH_INCLUDED = "included";
    private static final String PUBLISH_META = "meta";


    private HttpClient aClient;

    public enum PayloadObjectType {
        DATA, INCLUDED, META
    }

    public JsonSpecUtils(@Autowired HttpClient aClient) {
        this.aClient = aClient;
    }

    public JsonElement getPayloadObjectAsJsonElement(String jsonRawPayload, String jsonPath) {

        Configuration conf = Configuration.builder().jsonProvider(new GsonJsonProvider()).build();
        ReadContext documentCtx = JsonPath.using(conf).parse(jsonRawPayload);

        JsonElement jsonElement = null;
        try {
            jsonElement = documentCtx.read(jsonPath);
        } catch (PathNotFoundException ppathNotFoundEx) {
            // Element not present...
        }
        return jsonElement;

    }

    public String createPublishableObject(String rawPayload, Map<String, String> supportedDataTypes) {

        JsonElement dataObject = getPayloadObjectAsJsonElement(rawPayload, JSON_PATH_DATA);

        JsonElement includedArray = getPayloadObjectAsJsonElement(rawPayload, JSON_PATH_INCLUDED);
        if (includedArray != null)
            processIncluded(includedArray, supportedDataTypes);

        JsonElement metaObject = getPayloadObjectAsJsonElement(rawPayload, JSON_PATH_META);
        if (metaObject != null)
            processMeta(metaObject);

        // Create the new Object
        //
        JsonObject newData = new JsonObject();
        newData.add(PUBLISH_DATA, dataObject);

        if (includedArray != null)
            newData.add(PUBLISH_INCLUDED, includedArray);
        
        if (metaObject != null)
            newData.add(PUBLISH_META, metaObject);

        return newData.toString();

    }

    /**
     * Processing of the included section of a DINA compiant json api object.
     * 
     * In this current implementation we are resolving entries that are missing
     * their attributes property.
     * 
     * @param includedArray
     * @param supportedDataTypes
     */
    public void processIncluded(JsonElement includedArray, Map<String, String> supportedDataTypes) {

        if (includedArray == null || !includedArray.isJsonArray()) {
            return;
        }

        // Start processing each entry of the included data
        //
        for (int i = 0; i < includedArray.getAsJsonArray().size(); i++) {
            JsonObject curObject = includedArray.getAsJsonArray().get(i).getAsJsonObject();

            if (curObject.get("attributes") != null) {
                // Already have the attributes...just skip the current entry
                continue;
            }

            // Getting the type and perform a level #1 retrieval of attributes
            //
            String type = curObject.get("type").getAsString();
            if (supportedDataTypes.containsKey(type)) {

                // Get the Id and retrived the attributes from the related object.
                //
                String curObjectId = curObject.get("id").getAsString();
                String rawPayload = null;
                if ("person".equalsIgnoreCase(type)) {
                    rawPayload = aClient.getPerson(curObjectId, GetPerson.INCLUDED_RELATIONSHIPS_STRING); // TODO: Fix
                                                                                                          // that
                } else {

                    // Special case for managed-attribute-map, we will drop the 'metadata' prefix
                    //
                    String urlEntry = curObjectId.startsWith("managed-attribute-map")
                            ? curObjectId.substring(curObjectId.lastIndexOf("metadata/"))
                            : curObjectId;

                    rawPayload = aClient.getMetadata(urlEntry, GetMetadata.INCLUDED_RELATIONSHIPS_STRING);
                }

                // Take the data.attributes section to be embedded....
                //
                JsonElement dataObject = getPayloadObjectAsJsonElement(rawPayload, JSON_PATH_DATA_ATTRIBUTES);

                // At this stage we have the type, id and attributes for the object
                //
                // First pass, we can embed the object right away...
                //
                curObject.add("attributes", dataObject);

            }
        }
    }

    /**
     * Processing just the meta section of a DINA compliant object. For now we are
     * simply removing the external, we no longer need it in the object to be pushed
     * to elasticsearch.
     * 
     * @param metaObject meta section of the DINA json api object
     */
    public void processMeta(JsonElement metaObject) {

        if (metaObject == null || !metaObject.isJsonObject()) {
            return;
        }

        // Remove the external entry
        metaObject.getAsJsonObject().remove("external");
    }

}
