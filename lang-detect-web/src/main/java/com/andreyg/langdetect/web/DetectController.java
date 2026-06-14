package com.andreyg.langdetect.web;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.andreyg.langdetect.DetectionProperties;

import language.util.Pair;

@RestController
@RequestMapping("/api")
public class DetectController {

  private final DetectionService service;
  private final DetectionProperties props;

  public DetectController(DetectionService service, DetectionProperties props) {
    this.service = service;
    this.props = props;
  }

  /**
   * Exposes the server-side validation limits so the UI can enforce the same
   * bounds without hard-coding them. The server remains authoritative — this is
   * only to keep the client copy in sync.
   */
  @GetMapping("/config")
  public DetectConfig config() {
    return new DetectConfig(props.getMinLength(), props.getMaxLength());
  }

  public record DetectConfig(int minLength, int maxLength) {}

  @PostMapping("/detect")
  public ResponseEntity<DetectResponse> detect(@RequestBody DetectRequest request) {
    String text = request.text() == null ? "" : request.text();
    String mode = request.mode() == null ? "single" : request.mode().trim().toLowerCase();
    if (!mode.equals("single") && !mode.equals("multi")) {
      return ResponseEntity.badRequest()
          .body(new DetectResponse(null, null, null, null, "mode must be \"single\" or \"multi\""));
    }

    if (text.length() > 0 && text.length() < props.getMinLength()) {
      return ResponseEntity.badRequest()
          .body(
              new DetectResponse(
                  null,
                  null,
                  null,
                  null,
                  "Minimum length for language detection is "
                      + props.getMinLength()
                      + " characters"));
    }
    if (text.length() > props.getMaxLength()) {
      return ResponseEntity.badRequest()
          .body(
              new DetectResponse(
                  null,
                  null,
                  null,
                  null,
                  "Maximum length for language detection is "
                      + props.getMaxLength()
                      + " characters"));
    }

    if (text.isEmpty()) {
      return ResponseEntity.ok(new DetectResponse(mode, null, null, null, null));
    }

    try {
      if ("multi".equals(mode)) {
        List<Pair<String, Locale>> tagged = service.detectMulti(text);
        List<DetectResponse.Segment> segments =
            tagged.stream()
                .map(
                    p -> {
                      Locale loc = p.second();
                      return new DetectResponse.Segment(
                          p.first(), loc.getDisplayLanguage(), loc.toLanguageTag());
                    })
                .toList();
        return ResponseEntity.ok(new DetectResponse(mode, null, null, segments, null));
      }

      Locale lang = service.detectSingle(text);
      return ResponseEntity.ok(
          new DetectResponse(
              mode, lang.getDisplayLanguage(), lang.toLanguageTag(), null, null));
    } catch (IOException e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(new DetectResponse(null, null, null, null, "Detection failed: " + e.getMessage()));
    }
  }
}
