package com.andreyg.langdetect.web;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.andreyg.langdetect.DetectionProperties;

import language.model.NgramLanguageDetector;
import language.model.multiling.LanguageBoundaryDetector;
import language.util.Pair;

/**
 * Application service around the core detectors. Adds a small bounded result
 * cache for repeated short inputs and privacy-safe logging (length + outcome
 * only — never the user's text).
 */
@Service
public class DetectionService {

  private static final Logger log = LoggerFactory.getLogger(DetectionService.class);

  private final NgramLanguageDetector detector;
  private final LanguageBoundaryDetector boundaryDetector;
  private final DetectionProperties props;

  private final Map<String, Locale> singleCache;
  private final Map<String, List<Pair<String, Locale>>> multiCache;

  public DetectionService(
      NgramLanguageDetector detector,
      LanguageBoundaryDetector boundaryDetector,
      DetectionProperties props) {
    this.detector = detector;
    this.boundaryDetector = boundaryDetector;
    this.props = props;
    this.singleCache = boundedCache(props.getCacheMaxEntries());
    this.multiCache = boundedCache(props.getCacheMaxEntries());
  }

  private static <V> Map<String, V> boundedCache(int maxEntries) {
    return Collections.synchronizedMap(
        new LinkedHashMap<String, V>(64, 0.75f, true) {
          @Override
          protected boolean removeEldestEntry(Map.Entry<String, V> eldest) {
            return size() > maxEntries;
          }
        });
  }

  /** Detects the single most likely language for {@code text}. */
  public Locale detectSingle(String text) throws IOException {
    boolean cacheable = isCacheable(text);
    if (cacheable) {
      Locale hit = singleCache.get(text);
      if (hit != null) {
        log.info("detect mode=single len={} cache=hit -> {}", text.length(), hit.toLanguageTag());
        return hit;
      }
    }
    Locale result = detector.getMostLikelyLanguage(text);
    if (cacheable && result != null) {
      singleCache.put(text, result);
    }
    log.info(
        "detect mode=single len={} cache=miss -> {}",
        text.length(),
        result == null ? "null" : result.toLanguageTag());
    return result;
  }

  /** Splits {@code text} into language-tagged segments. */
  public List<Pair<String, Locale>> detectMulti(String text) throws IOException {
    boolean cacheable = isCacheable(text);
    if (cacheable) {
      List<Pair<String, Locale>> hit = multiCache.get(text);
      if (hit != null) {
        log.info("detect mode=multi len={} cache=hit -> {} segments", text.length(), hit.size());
        return hit;
      }
    }
    List<Pair<String, Locale>> result = boundaryDetector.tagStringWithLanguages(text);
    if (cacheable && result != null) {
      multiCache.put(text, result);
    }
    log.info(
        "detect mode=multi len={} cache=miss -> {} segments",
        text.length(),
        result == null ? 0 : result.size());
    return result;
  }

  private boolean isCacheable(String text) {
    return text != null && text.length() <= props.getCacheMaxTextLength();
  }
}
