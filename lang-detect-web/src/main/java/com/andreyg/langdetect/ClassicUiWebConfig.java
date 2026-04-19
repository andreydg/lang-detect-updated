package com.andreyg.langdetect;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ClassicUiWebConfig implements WebMvcConfigurer {

  @Override
  public void addViewControllers(ViewControllerRegistry registry) {
    // Ensure "/" serves the SPA: welcome-page handling for "/" is unreliable in some setups
    // (e.g. Spring Boot 4 behind Render), while "/index.html" is always served from static/.
    registry.addViewController("/").setViewName("forward:/index.html");
    // Subpaths like "/classic/" do not resolve to classic/index.html; redirect explicitly.
    registry.addRedirectViewController("/classic", "/classic/index.html");
    registry.addRedirectViewController("/classic/", "/classic/index.html");
  }
}
