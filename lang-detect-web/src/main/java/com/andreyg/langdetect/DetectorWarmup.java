package com.andreyg.langdetect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.andreyg.langdetect.web.DetectionService;

/**
 * Primes the detection pipeline at startup so the first real request does not
 * pay the cold-start cost (tokenizer/JIT warm-up, lazy classifier init). Runs a
 * representative single- and multi-language detection on a sample string.
 *
 * <p>On Cloud Run this happens during the startup-probe window while CPU is
 * boosted, keeping first-request latency low. Disable with
 * {@code langdetect.warmup=false}.
 */
@Component
@ConditionalOnProperty(prefix = "langdetect", name = "warmup", matchIfMissing = true)
public class DetectorWarmup implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DetectorWarmup.class);

  private static final String SAMPLE =
      "This is a warm-up sentence in English. "
          + "Ceci est une phrase d'échauffement en français.";

  private final DetectionService service;

  public DetectorWarmup(DetectionService service) {
    this.service = service;
  }

  @Override
  public void run(ApplicationArguments args) {
    long start = System.nanoTime();
    try {
      service.detectSingle(SAMPLE);
      service.detectMulti(SAMPLE);
      log.info("Detector warm-up complete in {} ms", (System.nanoTime() - start) / 1_000_000);
    } catch (Exception ex) {
      // never block startup on warm-up
      log.warn("Detector warm-up failed (continuing): {}", ex.getMessage());
    }
  }
}
