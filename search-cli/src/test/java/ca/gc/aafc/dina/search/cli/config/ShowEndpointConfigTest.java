package ca.gc.aafc.dina.search.cli.config;

import ca.gc.aafc.dina.search.cli.commands.ShowEndpointConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for show endpoint cli command.
 */
@SpringBootTest(properties = { "spring.shell.interactive.enabled=false" })
@EnableAutoConfiguration(exclude={DataSourceAutoConfiguration.class})
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

    ApiResourceDescriptor apiResourceDescriptor = serviceEndpointProperties.getApiResourceDescriptorForType("storage-unit");

    assertNotNull(serviceEndpointProperties.getIndexSettingDescriptorForType("metadata"));
    assertNotNull(serviceEndpointProperties.getIndexSettingDescriptorForType("person"));
    assertNotNull(apiResourceDescriptor);

    assertEquals("http://localhost:8081/api/v1/metadata", serviceEndpointProperties.getApiResourceDescriptorForType("metadata").url());
    assertEquals("http://localhost:8082/api/v1/person", serviceEndpointProperties.getApiResourceDescriptorForType("person").url());
    assertEquals("http://localhost:8085/api/v1/material-sample", serviceEndpointProperties.getApiResourceDescriptorForType("material-sample").url());
    assertEquals("http://localhost:8085/api/v1/storage-unit", apiResourceDescriptor.url());
  }
}
