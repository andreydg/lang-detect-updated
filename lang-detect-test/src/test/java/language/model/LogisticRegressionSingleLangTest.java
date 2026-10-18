package language.model;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import language.model.NgramLanguageDetector.ClassificationAlgorithm;

/**
 * Single language test with logistic classifier
 *
 * @author Andrey Gusev
 */
class LogisticRegressionSingleLangTest extends BaseSingleLangTest {

	private static final Logger log = LoggerFactory.getLogger(LogisticRegressionSingleLangTest.class);

	private static final int LINES_PER_LANGUAGE = 500;

	// basic check with logistic classifier
	@Test
	void testBasicPhrase() throws Exception {
		_testBasicPhrase(ClassificationAlgorithm.LOGISTIC_CLASSIFIER);
	}

	// check longer strings
	@Test
	void testLargeEnglishString() throws Exception {
		_testLargeEnglishString(ClassificationAlgorithm.LOGISTIC_CLASSIFIER);
	}

	// the persisted weights are positional: if the feature order drifts from the
	// weight layout (as HashMap order did between JDK 7 and 8), accuracy collapses.
	// The test split is held out from the classifier's training split, but most
	// of its lines also occur in the corpus the n-gram models were built from,
	// so this guards against regressions rather than estimating accuracy on new text.
	@Test
	void testTestSplitAccuracy() throws Exception {
		NgramLanguageDetector detector = NgramLanguageDetectorForTests.get();
		Path testSetDir = detector.getBasePath()
				.resolve(NgramLanguageDetector.BASE_MODEL_DIR)
				.resolve(NgramLanguageDetector.TRAINING_TEST_DIR);

		int total = 0, correct = 0;
		for (Locale locale : NgramLanguageDetector.getLocales()) {
			List<String> lines;
			try (Stream<String> s = Files.lines(testSetDir.resolve(locale + "_test"), StandardCharsets.UTF_8)) {
				lines = s.filter(line -> !line.isBlank()).limit(LINES_PER_LANGUAGE).toList();
			}
			for (String line : lines) {
				total++;
				if (locale.equals(detector.getMostLikelyLanguage(line, ClassificationAlgorithm.LOGISTIC_CLASSIFIER))) {
					correct++;
				}
			}
		}

		double accuracy = (double) correct / total;
		log.info("Logistic classifier accuracy on {} test-split lines: {}", total, accuracy);
		assertTrue(accuracy >= 0.90, "Accuracy on test-split lines was " + accuracy);
	}

}
