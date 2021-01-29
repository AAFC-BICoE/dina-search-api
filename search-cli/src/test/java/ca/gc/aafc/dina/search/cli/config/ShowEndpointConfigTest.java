package ca.gc.aafc.dina.search.cli.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.BeforeEach;
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
  
  private StringBuilder expectedEndpoints;

  @BeforeEach
  void setup() {
    expectedEndpoints = new StringBuilder();
    expectedEndpoints.append(System.lineSeparator());
    expectedEndpoints.append("****** Endpoints Configuration ******" + System.lineSeparator());
    expectedEndpoints.append("metadata=http://localhost:8081/api/v1/metadata" + System.lineSeparator());    
    expectedEndpoints.append("organization=http://localhost:8082/api/v1/organization" + System.lineSeparator());
    expectedEndpoints.append("person=http://localhost:8082/api/v1/person" + System.lineSeparator());
    expectedEndpoints.append("*************************************" + System.lineSeparator());
  }

  @DisplayName("Test Show Endpoint Config Command")
  @Test
  public void showEndpointConfig() {
    assertNotNull(showEndpointConfig.showEndpointConfig());
    assertNotEquals("", showEndpointConfig.showEndpointConfig()); 
    assertEquals(expectedEndpoints.toString(), showEndpointConfig.showEndpointConfig().toString()); 
  }
}
