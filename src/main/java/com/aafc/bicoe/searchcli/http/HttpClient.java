package com.aafc.bicoe.searchcli.http;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;

import org.springframework.context.annotation.Scope;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.HttpUrl.Builder;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


@Slf4j
@Service
@Scope("singleton")
public class HttpClient {
    
    private static final String HTTP_LOCALHOST_8082_API_V1_ORGANIZATION = "http://localhost:8082/api/v1/organization";
    private static final String HTTP_KEYCLOAK_LOCAL_8080_AUTH_REALMS_DINA_PROTOCOL_OPENID_CONNECT_TOKEN = "http://keycloak.local:8080/auth/realms/dina/protocol/openid-connect/token";
    private static final String HTTP_LOCALHOST_8081_API_V1_METADATA = "http://localhost:8081/api/v1/metadata";
    private static final String HTTP_LOCALHOST_8082_API_V1_PERSON = "http://localhost:8082/api/v1/person";

    private String latestToken;

    public String getToken() {

        String responseStr = null;
        OkHttpClient client = new OkHttpClient().newBuilder().connectTimeout(60, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build();
        RequestBody formBody = new FormBody.Builder()
                                            .add("client_id", "objectstore")
                                            .add("username", "cnc-cm")
                                            .add("password", "cnc-cm")
                                            .add("grant_type", "password")
                                            .build();
        Request request = 
            new Request.Builder()
                    .url(HTTP_KEYCLOAK_LOCAL_8080_AUTH_REALMS_DINA_PROTOCOL_OPENID_CONNECT_TOKEN)
                    .post(formBody)
                        .addHeader("Content-Type", "application/x-www-form-urlencoded")
                        .addHeader("Connection", "Keep-Alive")
                        .addHeader("Accept-Encoding", "application/json").build();
        try {
            Response response = client.newCall(request).execute();
            if (response.isSuccessful()) {
                responseStr = response.body().string();

                Gson gson = new Gson();
                KeyCloakAuthentication keyCloadAuth = gson.fromJson(responseStr, KeyCloakAuthentication.class);
            
                latestToken = keyCloadAuth.getAccessToken();
            }
        } catch (IOException exception) {
            exception.printStackTrace();
        }

        return latestToken;        
    }

    public String getOrganization(String orgId, String includedRelationshipsFields) {
        return this.getDataFromService(HTTP_LOCALHOST_8082_API_V1_ORGANIZATION, orgId, includedRelationshipsFields);        
    }

    public String getPerson(String personId, String includedRelationshipsFields) {
        return this.getDataFromService(HTTP_LOCALHOST_8082_API_V1_PERSON, personId, includedRelationshipsFields);        
    }
    
    public String getMetadata(String metaDataId, String includedRelationshipsFields) {
        return this.getDataFromService(HTTP_LOCALHOST_8081_API_V1_METADATA, metaDataId, includedRelationshipsFields);        
    }

    public String getDataFromService(String targetUrl, @Nullable String objectId, String includeQueryParams) {

        if (!StringUtils.hasText(latestToken)) {
            log.error("Please call get-token to either create or refresh your current token");        
        }

        String prettyPrintData = null;
        OkHttpClient client = new OkHttpClient().newBuilder().connectTimeout(60, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build();

        String pathParam = objectId == null ? "" : objectId;
        Builder urlBuilder = HttpUrl.parse(targetUrl).newBuilder();

        if (includeQueryParams != null) {
            urlBuilder.addQueryParameter("include", includeQueryParams);
        }
        urlBuilder.addPathSegment(pathParam);
        HttpUrl route = urlBuilder.build();
        
        Request request = 
            new Request.Builder()
                    .url(route)
                    .header("Authorization", "Bearer " + this.latestToken)
                    .header("crnk-compact", "true")
                    .get()
                        .addHeader("Connection", "Keep-Alive")
                        .addHeader("Accept-Encoding", "application/json").build();
        try {
            Response response = client.newCall(request).execute();
            if (response.isSuccessful()) {
                prettyPrintData = response.body().string();
            }
        } catch (IOException exception) {
            exception.printStackTrace();
        }

        return prettyPrintData;        

    }
}
