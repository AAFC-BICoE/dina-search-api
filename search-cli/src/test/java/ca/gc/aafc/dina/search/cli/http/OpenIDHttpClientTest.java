package ca.gc.aafc.dina.search.cli.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.NoRouteToHostException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import ca.gc.aafc.dina.search.cli.config.ServiceEndpointProperties;
import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;

@SpringBootTest(properties = { "spring.shell.interactive.enabled=false" })
public class OpenIDHttpClientTest {

  @Autowired
  private OpenIDHttpClient openIdClient;

  @Autowired
  private ServiceEndpointProperties serviceEndpointProperties;

  @DisplayName("Test Get Data from URL with Null EndpointDescriptor")
  @Test
  public void getDataFromUrlWithNullEndpointDescriptor() {

    assertNotNull(openIdClient);

    Assertions.assertThrows(
      SearchApiException.class, () -> {
        openIdClient.getDataFromUrl(serviceEndpointProperties.getEndpoints().get("Unknow Type"));
      });

  }

  @DisplayName("Test Get Data from URL with Null EndpointDescriptor")
  @Test
  public void getDataFromUrlWithPersonEndpointDescriptor() {

    assertNotNull(openIdClient);
    assertEquals("http://localhost:8082/api/v1/person", serviceEndpointProperties.getEndpoints().get("person").getTargetUrl());

    Exception exception = 
      Assertions.assertThrows(
        SearchApiException.class, () -> {
          openIdClient.getDataFromUrl(serviceEndpointProperties.getEndpoints().get("person"));
        });

    // validate that we et the proper exception
    //
    String expectedMessage = "Authentication rejected";
    assertEquals(expectedMessage, exception.getMessage());

    // Validate that the cause is an IOException NoRouteToHostException
    assertEquals(NoRouteToHostException.class, exception.getCause().getClass());
    
  }

}
