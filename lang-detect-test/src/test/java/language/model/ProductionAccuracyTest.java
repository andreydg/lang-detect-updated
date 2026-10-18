package language.model;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
 * Accuracy of the algorithm the web app uses, on the full held-out test set,
 * checked against {@code accuracy-baseline.properties}. Writes
 * {@code target/accuracy-report.md}, which CI shows on the run's summary page.
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
		Map<String, Double> measured = new LinkedHashMap<>();
		baseline.put("overall", Double.parseDouble(props.getProperty("overall")));
		measured.put("overall", report.accuracy());
		for (Locale locale : NgramLanguageDetector.getLocales()) {
			baseline.put(locale.toString(), Double.parseDouble(props.getProperty(locale.toString())));
			measured.put(locale.toString(), report.accuracy(locale));
		}

		Files.createDirectories(REPORT.getParent());
		Files.writeString(REPORT, report.toMarkdown(baseline), StandardCharsets.UTF_8);
		log.info("{} accuracy on {} held-out lines: {}% (report: {})", PRODUCTION, report.lines(),
				String.format(Locale.ROOT, "%.2f", report.accuracy()), REPORT.toAbsolutePath());

		List<String> regressions = new ArrayList<>();
		for (Map.Entry<String, Double> expected : baseline.entrySet()) {
			double now = measured.get(expected.getKey());
			if (now < expected.getValue() - tolerance) {
				regressions.add(String.format(Locale.ROOT, "%s %.2f%% < %.2f%%", expected.getKey(), now,
						expected.getValue()));
			} else if (now > expected.getValue() + tolerance) {
				log.info("{} accuracy rose to {}% (baseline {}%): raise it in accuracy-baseline.properties",
						expected.getKey(), String.format(Locale.ROOT, "%.2f", now), expected.getValue());
			}
		}
		assertTrue(regressions.isEmpty(), "Accuracy fell below the baseline: " + regressions);
	}
}
