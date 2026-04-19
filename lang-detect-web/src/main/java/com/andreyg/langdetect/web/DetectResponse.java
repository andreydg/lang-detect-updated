package com.andreyg.langdetect.web;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DetectResponse(
    String mode,
    String language,
    String languageTag,
    List<Segment> segments,
    String error) {

  public record Segment(String text, String language, String languageTag) {}
}
