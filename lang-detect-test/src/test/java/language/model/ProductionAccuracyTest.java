package language.model;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import language.model.NgramLanguageDetector.ClassificationAlgorithm;

/**
 * Regression benchmark for the algorithm the web app uses: accuracy on the full
 * existing test split, checked against {@code accuracy-baseline.properties}.
 * Writes {@code target/accuracy-report.md}, which CI shows on the run's summary
 * page. The test lines come from the same source texts as the n-gram models, so
 * the numbers track regressions; they don't estimate accuracy on new text.
 *
 * @author Andrey Gusev
 */
class ProductionAccuracyTest {

	private static final Logger log = LoggerFactory.getLogger(ProductionAccuracyTest.class);

	private static final ClassificationAlgorithm PRODUCTION = ClassificationAlgorithm.LINEAR_WEIGHTS;
	private static final Path REPORT = Path.of("target", "accuracy-report.md");

	// accuracy must not drop below the baseline; when a change improves it,
	// raise the baseline in the same change
	@Test
	void testAccuracyIsNotBelowBaseline() throws Exception {
		NgramLanguageDetector detector = NgramLanguageDetectorForTests.get();
		Path testSetDir = detector.getBasePath()
				.resolve(NgramLanguageDetector.BASE_MODEL_DIR)
				.resolve(NgramLanguageDetector.TRAINING_TEST_DIR);
		AccuracyReport report = AccuracyReport.evaluate(detector, PRODUCTION, testSetDir);

		Properties props = new Properties();
		try (InputStream in = getClass().getResourceAsStream("/accuracy-baseline.properties")) {
			props.load(in);
		}
		double tolerance = Double.parseDouble(props.getProperty("tolerance"));
		Map<String, Double> baseline = new LinkedHashMap<>();
		baseline.put("overall", Double.parseDouble(props.getProperty("overall")));
		for (Locale locale : NgramLanguageDetector.getLocales()) {
			baseline.put(locale.toString(), Double.parseDouble(props.getProperty(locale.toString())));
		}

		Files.createDirectories(REPORT.getParent());
		Files.writeString(REPORT, report.toMarkdown(baseline), StandardCharsets.UTF_8);
		log.info("{} accuracy on {} test-split lines: {}% (report: {})", PRODUCTION, report.lines(),
				String.format(Locale.ROOT, "%.2f", report.accuracy()), REPORT.toAbsolutePath());

		List<String> improvements = AccuracyReport.improvements(report.accuracies(), baseline, tolerance);
		if (!improvements.isEmpty()) {
			log.info("Accuracy rose above the baseline: {}; raise it in accuracy-baseline.properties", improvements);
		}
		List<String> regressions = AccuracyReport.regressions(report.accuracies(), baseline, tolerance);
		assertTrue(regressions.isEmpty(), "Accuracy fell below the baseline: " + regressions);
	}
}
