package com.andreyg.langdetect.web;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import com.andreyg.langdetect.DetectionProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Rejects oversized request bodies with 413 before the framework buffers and
 * parses them. The controller still enforces the character-level limit; this
 * filter is the cheap, early byte-level guard (defense against large uploads).
 */
public class PayloadLimitFilter extends OncePerRequestFilter {

  private final long maxPayloadBytes;

  public PayloadLimitFilter(DetectionProperties props) {
    this.maxPayloadBytes = props.getMaxPayloadBytes();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    long declared = request.getContentLengthLong();
    if (declared > maxPayloadBytes) {
      response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.getWriter().write("{\"error\":\"Request body too large\"}");
      return;
    }
    chain.doFilter(request, response);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    // only guard the API; static assets and other paths are unaffected
    return !request.getRequestURI().startsWith("/api/");
  }
}
