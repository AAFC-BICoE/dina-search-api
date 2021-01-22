package ca.gc.aafc.dina.search.cli.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import lombok.Data;

@Component
@Data
@PropertySource(value = "classpath:endpoints.yml")
@PropertySource(value = "file:config/endpoints.yml", ignoreResourceNotFound = true)
public class ServiceEndpointProperties {
  
  private static final String ENDPOINT_PERSON = "${endpoint.person}";
  private static final String ENDPOINT_ORGANIZATION = "${endpoint.organization}";
  private static final String ENDPOINT_METADATA = "${endpoint.metadata}";

  @Value(ENDPOINT_PERSON)
  private String personEndpoint;

  @Value(ENDPOINT_ORGANIZATION)
  private String organizationEndpoint;

  @Value(ENDPOINT_METADATA)
  private String metadataEndpoint;

}
