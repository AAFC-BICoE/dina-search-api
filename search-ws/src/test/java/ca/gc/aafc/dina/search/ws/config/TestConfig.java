package ca.gc.aafc.dina.search.ws.config;

import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

@Configuration
public class TestConfig {

  @Bean
  public BuildProperties buildProperties() {
    Properties props = new Properties();
    props.setProperty("version", "test-api-version");
    return new BuildProperties(props);
  }
}
