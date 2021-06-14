package ca.gc.aafc.dina.search.cli;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

// CHECKSTYLE:OFF HideUtilityClassConstructor (Configuration class can not have
// invisible constructor, ignore the check style error for this case)
@SpringBootApplication
@EnableAutoConfiguration
@ComponentScan("ca.gc.aafc.dina.search")
public class SearchCliApplication {
  public static void main(String[] args) {
    SpringApplication.run(SearchCliApplication.class, args);
  }
}
