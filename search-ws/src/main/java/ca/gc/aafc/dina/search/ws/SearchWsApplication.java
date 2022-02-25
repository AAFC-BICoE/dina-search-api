package ca.gc.aafc.dina.search.ws;

import org.javers.spring.boot.sql.JaversSqlAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

import ca.gc.aafc.dina.security.KeycloakAuthConfig;
import ca.gc.aafc.dina.security.KeycloakDisabledAuthConfig;

// CHECKSTYLE:OFF HideUtilityClassConstructor (Configuration class can not have
// invisible constructor, ignore the check style error for this case)
@SpringBootApplication
@ImportAutoConfiguration(
  classes = {KeycloakAuthConfig.class, KeycloakDisabledAuthConfig.class}, 
  exclude = {DataSourceAutoConfiguration.class, JaversSqlAutoConfiguration.class}
)
public class SearchWsApplication {

  public static void main(String[] args) {
    SpringApplication.run(SearchWsApplication.class, args);
  }
}
