package language.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;

import language.classifier.Classifier;

/**
 * How {@link NgramLanguageDetector#detectLanguageClassifier} picks a language
 * from the one-vs-rest classifier scores
 *
 * @author Andrey Gusev
 */
class ClassifierSelectionTest {

	private final NgramLanguageDetector detector = NgramLanguageDetectorForTests.get();

	/** Gives every example the same score. */
	private record FixedScore(double score) implements Classifier<Double, Locale, LanguageDocumentExample> {

		@Override
		public void train(List<LanguageDocumentExample> trainingSet) {
		}

		@Override
		public double getConfidenceLevel(LanguageDocumentExample example) {
			return score;
		}

		@Override
		public void write(DataOutput output) {
		}

		@Override
		public boolean read(DataInput input) {
			return false;
		}
	}

	/** Scores in LOCALES order: en, fr, it, de, es, pt. */
	private Locale pick(double... scores) throws Exception {
		Locale[] locales = NgramLanguageDetector.getLocales();
		Map<Locale, FixedScore> classifiers = new HashMap<>();
		for (int ind = 0; ind < locales.length; ind++) {
			classifiers.put(locales[ind], new FixedScore(scores[ind]));
		}
		return detector.detectLanguageClassifier(BaseSingleLangTest.getEnglishString(), classifiers, false);
	}

	// log-odds are often all negative; the highest still wins
	@Test
	void testPicksHighestScoreWhenAllNegative() throws Exception {
		assertEquals(Locale.GERMAN, pick(-9, -7, -8, -2, -5, -6), "Didn't pick the highest score");
	}

	// ties go to the first locale in LOCALES order, so answers are reproducible
	@Test
	void testBreaksTiesInLocaleOrder() throws Exception {
		for (int run = 0; run < 5; run++) {
			assertEquals(Locale.FRENCH, pick(0.2, 1.0, 1.0, 0.3, 1.0, 0.1), "Tie not broken in LOCALES order");
		}
	}

	@Test
	void testNoScoreMeansNoLanguage() throws Exception {
		double nan = Double.NaN;
		assertNull(pick(nan, nan, nan, nan, nan, nan), "Should not pick a language without scores");
	}
}
