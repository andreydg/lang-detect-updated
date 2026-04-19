package com.andreyg.langdetect.web;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import language.model.NgramLanguageDetector;
import language.model.multiling.LanguageBoundaryDetector;
import language.util.Pair;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class DetectController {

  private static final int MIN_LENGTH = 25;
  private static final int MAX_LENGTH = 100_000;

  private final NgramLanguageDetector detector;
  private final LanguageBoundaryDetector boundaryDetector;

  public DetectController(
      NgramLanguageDetector detector, LanguageBoundaryDetector boundaryDetector) {
    this.detector = detector;
    this.boundaryDetector = boundaryDetector;
  }

  @PostMapping("/detect")
  public ResponseEntity<DetectResponse> detect(@RequestBody DetectRequest request) {
    String text = request.text() == null ? "" : request.text();
    String mode = request.mode() == null ? "single" : request.mode().trim().toLowerCase();
    if (!mode.equals("single") && !mode.equals("multi")) {
      return ResponseEntity.badRequest()
          .body(new DetectResponse(null, null, null, null, "mode must be \"single\" or \"multi\""));
    }

    if (text.length() > 0 && text.length() < MIN_LENGTH) {
      return ResponseEntity.badRequest()
          .body(
              new DetectResponse(
                  null,
                  null,
                  null,
                  null,
                  "Minimum length for language detection is " + MIN_LENGTH + " characters"));
    }
    if (text.length() > MAX_LENGTH) {
      return ResponseEntity.badRequest()
          .body(
              new DetectResponse(
                  null,
                  null,
                  null,
                  null,
                  "Maximum length for language detection is " + MAX_LENGTH + " characters"));
    }

    if (text.isEmpty()) {
      return ResponseEntity.ok(new DetectResponse(mode, null, null, null, null));
    }

    try {
      if ("multi".equals(mode)) {
        List<Pair<String, Locale>> tagged = boundaryDetector.tagStringWithLanguages(text);
        detector.logQuery(text);
        List<DetectResponse.Segment> segments =
            tagged.stream()
                .map(
                    p -> {
                      Locale loc = p.getSecond();
                      return new DetectResponse.Segment(
                          p.getFirst(), loc.getDisplayLanguage(), loc.toLanguageTag());
                    })
                .toList();
        return ResponseEntity.ok(new DetectResponse(mode, null, null, segments, null));
      }

      Locale lang = detector.getMostLikelyLanguage(text);
      detector.logQuery(text);
      return ResponseEntity.ok(
          new DetectResponse(
              mode, lang.getDisplayLanguage(), lang.toLanguageTag(), null, null));
    } catch (IOException e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(new DetectResponse(null, null, null, null, "Detection failed: " + e.getMessage()));
    }
  }
}
