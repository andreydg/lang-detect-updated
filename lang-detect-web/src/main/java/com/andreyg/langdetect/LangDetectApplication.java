package com.andreyg.langdetect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LangDetectApplication {

  public static void main(String[] args) {
    SpringApplication.run(LangDetectApplication.class, args);
  }
}
