package com.andreyg.langdetect;

import java.io.File;
import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.ResourcePatternResolver;

import language.model.NgramLanguageDetector;
import language.model.NgramLanguageDetector.ClassificationAlgorithm;
import language.model.multiling.LanguageBoundaryDetector;
import language.model.multiling.SlidingWindowBigramBoundaryDetector;

@Configuration
public class LangDetectConfiguration {

  @Bean
  public NgramLanguageDetector ngramLanguageDetector(ResourcePatternResolver resourceResolver)
      throws IOException {
    File base = LanguageModelsPaths.resolve(resourceResolver);
    return new NgramLanguageDetector(base);
  }

  @Bean
  public LanguageBoundaryDetector languageBoundaryDetector(NgramLanguageDetector detector)
      throws IOException {
    return new SlidingWindowBigramBoundaryDetector(
        ClassificationAlgorithm.LINEAR_WEIGHTS, detector, 4);
  }
}
