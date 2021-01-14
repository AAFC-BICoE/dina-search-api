package com.aafc.bicoe.searchcli;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableAutoConfiguration
public class SearchcliApplication {

	public static void main(String[] args) {
		SpringApplication.run(SearchcliApplication.class, args);
	}

}
