package ca.gc.aafc.dina.search.cli.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import ca.gc.aafc.dina.testsupport.DatabaseSupportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

import ca.gc.aafc.dina.search.cli.commands.ShowEndpointConfig;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

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

    EndpointDescriptor storageDescriptor = serviceEndpointProperties.getEndpoints().get("storage-unit");

    assertNotNull(serviceEndpointProperties.getEndpoints().get("metadata"));
    assertNotNull(serviceEndpointProperties.getEndpoints().get("person"));
    assertNotNull(storageDescriptor);

    assertEquals("http://localhost:8081/api/v1/metadata", serviceEndpointProperties.getEndpoints().get("metadata").getTargetUrl());
    assertEquals("http://localhost:8082/api/v1/person", serviceEndpointProperties.getEndpoints().get("person").getTargetUrl());
    assertEquals("http://localhost:8085/api/v1/material-sample", serviceEndpointProperties.getEndpoints().get("material-sample").getTargetUrl());
    assertEquals("http://localhost:8085/api/v1/storage-unit", storageDescriptor.getTargetUrl());
  }
}
