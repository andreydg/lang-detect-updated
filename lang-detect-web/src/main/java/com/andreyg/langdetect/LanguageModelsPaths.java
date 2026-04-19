package com.andreyg.langdetect;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

/**
 * Resolves the directory that contains {@code languagemodels/} (the base path
 * expected by {@link language.model.NgramLanguageDetector}).
 */
public final class LanguageModelsPaths {

  private LanguageModelsPaths() {}

  public static File resolve(ResourcePatternResolver resourceResolver) throws IOException {
    String env = System.getenv("LANG_DETECT_BASE_PATH");
    if (env != null && !env.isBlank()) {
      File base = new File(env.trim()).getCanonicalFile();
      requireModels(base);
      return base;
    }

    File cwd = new File(".").getCanonicalFile();
    if (new File(cwd, "languagemodels").isDirectory()) {
      return cwd;
    }

    File fromTarget = new File(cwd, "target/classes/languagemodels");
    if (fromTarget.isDirectory()) {
      return new File(cwd, "target/classes").getCanonicalFile();
    }

    Resource root = resourceResolver.getResource("classpath:/languagemodels/");
    if (!root.exists()) {
      throw new IllegalStateException(
          "Language models not found. Set LANG_DETECT_BASE_PATH to the directory "
              + "that contains the languagemodels folder (not the folder itself).");
    }

    try {
      File f = root.getFile();
      File parent = f.getParentFile();
      if (parent != null && new File(parent, "languagemodels").isDirectory()) {
        return parent.getCanonicalFile();
      }
    } catch (Exception ignored) {
      // unpack from classpath (e.g. executable jar)
    }

    return extractFromClasspath(resourceResolver);
  }

  private static void requireModels(File base) throws IOException {
    File lm = new File(base.getCanonicalFile(), "languagemodels");
    if (!lm.isDirectory()) {
      throw new IllegalArgumentException(
          "LANG_DETECT_BASE_PATH must point to a directory that contains languagemodels/: " + base);
    }
  }

  private static File extractFromClasspath(ResourcePatternResolver resolver) throws IOException {
    Resource[] resources = resolver.getResources("classpath:/languagemodels/**/*");
    if (resources.length == 0) {
      throw new IllegalStateException("No files under classpath:/languagemodels/ — check build resources.");
    }
    Path tmp = Files.createTempDirectory("lang-detect-models-");
    Path lmRoot = tmp.resolve("languagemodels");
    Files.createDirectories(lmRoot);
    for (Resource res : resources) {
      if (!res.isReadable()) {
        continue;
      }
      URL url = res.getURL();
      String urlString = url.toString();
      int marker = urlString.indexOf("languagemodels/");
      if (marker < 0) {
        continue;
      }
      String rel = urlString.substring(marker + "languagemodels/".length());
      rel = URLDecoder.decode(rel, StandardCharsets.UTF_8);
      if (rel.isEmpty() || rel.endsWith("/")) {
        continue;
      }
      Path out = lmRoot.resolve(rel);
      Path parent = out.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      try (InputStream in = res.getInputStream()) {
        Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
      }
    }
    return tmp.toFile();
  }
}
