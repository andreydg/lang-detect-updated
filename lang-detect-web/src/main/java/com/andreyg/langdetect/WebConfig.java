package com.andreyg.langdetect;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.andreyg.langdetect.web.PayloadLimitFilter;

/**
 * Web-layer wiring: CORS (driven by configuration, default closed) and the
 * early payload-size guard.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  private final DetectionProperties props;

  public WebConfig(DetectionProperties props) {
    this.props = props;
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    // Only open CORS when origins are explicitly configured; otherwise the API
    // stays same-origin (the bundled UI needs no cross-origin access).
    if (props.getAllowedOrigins().isEmpty()) {
      return;
    }
    registry
        .addMapping("/api/**")
        .allowedOrigins(props.getAllowedOrigins().toArray(String[]::new))
        .allowedMethods("GET", "POST");
  }

  @Bean
  public FilterRegistrationBean<PayloadLimitFilter> payloadLimitFilter() {
    FilterRegistrationBean<PayloadLimitFilter> reg =
        new FilterRegistrationBean<>(new PayloadLimitFilter(props));
    reg.addUrlPatterns("/api/*");
    reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return reg;
  }
}
