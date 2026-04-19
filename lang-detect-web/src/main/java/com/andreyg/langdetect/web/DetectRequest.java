package com.andreyg.langdetect.web;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DetectRequest(String text, String mode) {}
