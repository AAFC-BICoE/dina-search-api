package ca.gc.aafc.dina.search.cli.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

import ca.gc.aafc.dina.search.cli.config.ServiceEndpointProperties;
import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;

@SpringBootTest(properties = { "spring.shell.interactive.enabled=false" })
@EnableAutoConfiguration(exclude={DataSourceAutoConfiguration.class})
public class OpenIDHttpClientTest {

  @Autowired
  private OpenIDHttpClient openIdClient;

  @Autowired
  private ServiceEndpointProperties serviceEndpointProperties;

  @DisplayName("Test Get Data from URL with Null EndpointDescriptor")
  @Test
  public void getDataFromUrlWithNullEndpointDescriptor() {

    assertNotNull(openIdClient);
    Exception exception = Assertions.assertThrows(
      SearchApiException.class, () -> {
        openIdClient.getDataFromUrl(null, null);
      });
    String expectedMessage = "Invalid endpoint descriptor, can not be null";
    assertEquals(expectedMessage, exception.getMessage());
  }

  @DisplayName("Test Get Data from URL with valid EndpointDescriptor")
  @Test
  public void getDataFromUrlWithPersonEndpointDescriptor() {

    assertNotNull(openIdClient);
    assertEquals("http://localhost:8082/api/v1/person", serviceEndpointProperties.getApiResourceDescriptorForType("person").url());

    Exception exception =
        Assertions.assertThrows(
            SearchApiException.class, () -> {
              openIdClient.getDataFromUrl(serviceEndpointProperties.getApiResourceDescriptorForType("person"),
                  serviceEndpointProperties.getEndpointDescriptorForType("person")
              );
            });

    // validate that we et the proper exception
    // the exception is due to the fact that Keycloak url won't resolve but we know we got to the http request
    String expectedMessage = "Exception during retrieval from http://localhost:8082/api/v1/person/?include=organizations";
    assertEquals(expectedMessage, exception.getMessage());
  }

}
