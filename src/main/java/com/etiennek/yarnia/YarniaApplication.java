package com.etiennek.yarnia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class YarniaApplication {

	public static void main(String[] args) throws java.io.IOException {
		// SQLite creates the db file but not its parent directory.
		// Keep the default path in sync with spring.datasource.url in application.yml.
		final var dbPath = java.nio.file.Path.of(
				System.getenv().getOrDefault("YARNIA_DB_PATH", "./data/yarnia.db"));
		final var parent = dbPath.toAbsolutePath().getParent();
		if (parent != null) {
			java.nio.file.Files.createDirectories(parent);
		}

		SpringApplication.run(YarniaApplication.class, args);
	}

}


