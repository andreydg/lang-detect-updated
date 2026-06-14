package com.andreyg.langdetect;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized, tunable settings for the detection web layer. Bound from the
 * {@code langdetect.*} namespace (see {@code application.properties}); every
 * value has a sensible default so the app runs with no extra configuration.
 */
@ConfigurationProperties(prefix = "langdetect")
public class DetectionProperties {

  /** Minimum input length (characters) accepted by {@code /api/detect}. */
  private int minLength = 25;

  /** Maximum input length (characters) accepted by {@code /api/detect}. */
  private int maxLength = 100_000;

  /** Hard cap on the HTTP request body size in bytes; larger bodies get 413. */
  private long maxPayloadBytes = 1_048_576; // 1 MiB

  /**
   * CORS allowed origins for the API. Empty (the default) means no cross-origin
   * access is granted — the bundled UI is same-origin and needs none.
   */
  private List<String> allowedOrigins = List.of();

  /** Whether to warm the detector at startup so the first request is fast. */
  private boolean warmup = true;

  /** Max entries kept in the in-memory result cache. */
  private int cacheMaxEntries = 500;

  /** Only cache results for inputs at or below this length (characters). */
  private int cacheMaxTextLength = 4_096;

  public int getMinLength() {
    return minLength;
  }

  public void setMinLength(int minLength) {
    this.minLength = minLength;
  }

  public int getMaxLength() {
    return maxLength;
  }

  public void setMaxLength(int maxLength) {
    this.maxLength = maxLength;
  }

  public long getMaxPayloadBytes() {
    return maxPayloadBytes;
  }

  public void setMaxPayloadBytes(long maxPayloadBytes) {
    this.maxPayloadBytes = maxPayloadBytes;
  }

  public List<String> getAllowedOrigins() {
    return allowedOrigins;
  }

  public void setAllowedOrigins(List<String> allowedOrigins) {
    this.allowedOrigins = allowedOrigins;
  }

  public boolean isWarmup() {
    return warmup;
  }

  public void setWarmup(boolean warmup) {
    this.warmup = warmup;
  }

  public int getCacheMaxEntries() {
    return cacheMaxEntries;
  }

  public void setCacheMaxEntries(int cacheMaxEntries) {
    this.cacheMaxEntries = cacheMaxEntries;
  }

  public int getCacheMaxTextLength() {
    return cacheMaxTextLength;
  }

  public void setCacheMaxTextLength(int cacheMaxTextLength) {
    this.cacheMaxTextLength = cacheMaxTextLength;
  }
}
