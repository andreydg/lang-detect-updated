package com.andreyg.langdetect.web;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.andreyg.langdetect.DetectionProperties;

import language.model.NgramLanguageDetector;
import language.model.multiling.LanguageBoundaryDetector;
import language.util.Pair;

/**
 * Web-layer tests for {@link DetectController} and its supporting filter/advice.
 *
 * <p>Uses a standalone {@link MockMvc} over the real controller + service with
 * the core detectors mocked, so the tests run fast and need neither the on-disk
 * language models nor a full Spring context.
 */
class DetectControllerTest {

  private static final String VALID_TEXT = "This is a sufficiently long sample sentence.";

  private NgramLanguageDetector detector;
  private LanguageBoundaryDetector boundaryDetector;
  private DetectionProperties props;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    detector = mock(NgramLanguageDetector.class);
    boundaryDetector = mock(LanguageBoundaryDetector.class);
    props = new DetectionProperties();
    DetectionService service = new DetectionService(detector, boundaryDetector, props);
    mvc =
        MockMvcBuilders.standaloneSetup(new DetectController(service, props))
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new PayloadLimitFilter(props))
            .build();
  }

  private static String body(String text, String mode) {
    String modePart = mode == null ? "" : ",\"mode\":\"" + mode + "\"";
    return "{\"text\":\"" + text + "\"" + modePart + "}";
  }

  @Test
  void configExposesValidationLimits() throws Exception {
    mvc.perform(get("/api/config"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.minLength").value(25))
        .andExpect(jsonPath("$.maxLength").value(100000));
  }

  @Test
  void singleModeReturnsDetectedLanguage() throws Exception {
    when(detector.getMostLikelyLanguage(anyString())).thenReturn(Locale.ENGLISH);

    mvc.perform(
            post("/api/detect")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(VALID_TEXT, "single")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mode").value("single"))
        .andExpect(jsonPath("$.language").value(Locale.ENGLISH.getDisplayLanguage()))
        .andExpect(jsonPath("$.languageTag").value("en"))
        .andExpect(jsonPath("$.error").doesNotExist());
  }

  @Test
  void missingModeDefaultsToSingle() throws Exception {
    when(detector.getMostLikelyLanguage(anyString())).thenReturn(Locale.ENGLISH);

    mvc.perform(
            post("/api/detect")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(VALID_TEXT, null)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mode").value("single"))
        .andExpect(jsonPath("$.language").value(Locale.ENGLISH.getDisplayLanguage()));
  }

  @Test
  void multiModeReturnsSegments() throws Exception {
    when(boundaryDetector.tagStringWithLanguages(anyString()))
        .thenReturn(
            List.of(
                new Pair<>("Hello there", Locale.ENGLISH),
                new Pair<>("Bonjour ici", Locale.FRENCH)));

    mvc.perform(
            post("/api/detect")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(VALID_TEXT, "multi")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mode").value("multi"))
        .andExpect(jsonPath("$.segments.length()").value(2))
        .andExpect(jsonPath("$.segments[0].text").value("Hello there"))
        .andExpect(jsonPath("$.segments[0].languageTag").value("en"))
        .andExpect(jsonPath("$.segments[1].languageTag").value("fr"));
  }

  @Test
  void uppercaseAndPaddedModeIsNormalized() throws Exception {
    when(detector.getMostLikelyLanguage(anyString())).thenReturn(Locale.ENGLISH);

    mvc.perform(
            post("/api/detect")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(VALID_TEXT, "  SINGLE  ")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mode").value("single"));
  }

  @Test
  void invalidModeIsRejected() throws Exception {
    mvc.perform(
            post("/api/detect")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(VALID_TEXT, "sideways")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("mode must be \"single\" or \"multi\""));
  }

  @Test
  void textBelowMinLengthIsRejected() throws Exception {
    mvc.perform(
            post("/api/detect")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("too short", "single")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("Minimum length for language detection is 25 characters"));
  }

  @Test
  void textAboveMaxLengthIsRejected() throws Exception {
    String huge = "a".repeat(100_001);
    mvc.perform(
            post("/api/detect")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(huge, "single")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("Maximum length for language detection is 100000 characters"));
  }

  @Test
  void emptyTextReturnsOkWithNoResult() throws Exception {
    mvc.perform(
            post("/api/detect")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("", "single")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mode").value("single"))
        .andExpect(jsonPath("$.language").doesNotExist())
        .andExpect(jsonPath("$.error").doesNotExist());
  }

  @Test
  void detectionIoFailureMapsToServerError() throws Exception {
    when(detector.getMostLikelyLanguage(anyString())).thenThrow(new IOException("boom"));

    mvc.perform(
            post("/api/detect")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(VALID_TEXT, "single")))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.error").value("Detection failed: boom"));
  }

  @Test
  void uncheckedFailureMapsToGenericServerError() throws Exception {
    when(detector.getMostLikelyLanguage(anyString()))
        .thenThrow(new IllegalStateException("model parse error"));

    mvc.perform(
            post("/api/detect")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(VALID_TEXT, "single")))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.error").value("Internal server error"));
  }

  @Test
  void malformedJsonReturnsBadRequest() throws Exception {
    mvc.perform(
            post("/api/detect")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ this is not json"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("Malformed JSON request"));
  }

  @Test
  void identicalRequestsAreServedFromCache() throws Exception {
    when(detector.getMostLikelyLanguage(anyString())).thenReturn(Locale.ENGLISH);

    for (int i = 0; i < 3; i++) {
      mvc.perform(
              post("/api/detect")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body(VALID_TEXT, "single")))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.languageTag").value("en"));
    }
    // detector hit only once; subsequent identical requests came from the cache
    verify(detector, times(1)).getMostLikelyLanguage(anyString());
  }

  @Test
  void oversizedPayloadIsRejectedEarly() throws Exception {
    DetectionProperties tiny = new DetectionProperties();
    tiny.setMaxPayloadBytes(10);
    DetectionService service = new DetectionService(detector, boundaryDetector, tiny);
    MockMvc limited =
        MockMvcBuilders.standaloneSetup(new DetectController(service, tiny))
            .addFilters(new PayloadLimitFilter(tiny))
            .build();

    limited
        .perform(
            post("/api/detect")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(VALID_TEXT, "single")))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(jsonPath("$.error").value("Request body too large"));
  }
}
