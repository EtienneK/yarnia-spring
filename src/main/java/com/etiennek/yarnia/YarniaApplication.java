package com.etiennek.yarnia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class YarniaApplication {

	public static void main(String[] args) {
		SpringApplication.run(YarniaApplication.class, args);
	}

}


