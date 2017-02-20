package de.db.searchify;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
public class SearchifyApplication {

	public static void main(String[] args) {
		SpringApplication.run(SearchifyApplication.class, args);
	}
}
