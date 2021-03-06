package ca.gc.aafc.dina.search.cli.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import ca.gc.aafc.dina.search.cli.commands.ShowEndpointConfig;

/**
 * Unit test for show endpoint cli command.
 */
@SpringBootTest(properties = { "spring.shell.interactive.enabled=false" })
public class ShowEndpointConfigTest {

  @Autowired
  private ShowEndpointConfig showEndpointConfig;
  
  @Autowired
  private ServiceEndpointProperties serviceEndpointProperties;

  @DisplayName("Test Show Endpoint Config Command")
  @Test
  public void showEndpointConfig() {
    assertNotNull(showEndpointConfig.showEndpointConfig());
    assertNotEquals("", showEndpointConfig.showEndpointConfig()); 

    assertNotNull(serviceEndpointProperties.getEndpoints().get("metadata"));
    assertNotNull(serviceEndpointProperties.getEndpoints().get("organization"));
    assertNotNull(serviceEndpointProperties.getEndpoints().get("person"));

    assertEquals("http://localhost:8081/api/v1/metadata", serviceEndpointProperties.getEndpoints().get("metadata").getTargetUrl());
    assertEquals("http://localhost:8082/api/v1/organization", serviceEndpointProperties.getEndpoints().get("organization").getTargetUrl());
    assertEquals("http://localhost:8082/api/v1/person", serviceEndpointProperties.getEndpoints().get("person").getTargetUrl());
  }
}
