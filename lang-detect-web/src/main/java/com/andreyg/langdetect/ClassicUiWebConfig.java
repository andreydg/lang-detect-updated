package com.andreyg.langdetect;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ClassicUiWebConfig implements WebMvcConfigurer {

  @Override
  public void addViewControllers(ViewControllerRegistry registry) {
    // Spring Boot only serves index.html as a welcome page for "/"; subpaths like "/classic/"
    // do not resolve to classic/index.html, so redirect explicitly.
    registry.addRedirectViewController("/classic", "/classic/index.html");
    registry.addRedirectViewController("/classic/", "/classic/index.html");
  }
}
