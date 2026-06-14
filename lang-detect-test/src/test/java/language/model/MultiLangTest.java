package language.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import language.model.NgramLanguageDetector.ClassificationAlgorithm;
import language.model.multiling.LanguageBoundaryDetector;
import language.model.multiling.SlidingWindowBigramBoundaryDetector;
import language.util.Pair;

/**
 * Test multi-lang strings
 *
 * @author Andrey Gusev
 */
class MultiLangTest {

	// only checks the order, doesn't check the boundaries
	@Test
	void testMultiLangOrder() throws Exception {

		LanguageBoundaryDetector boundaryDetector = new SlidingWindowBigramBoundaryDetector(
				ClassificationAlgorithm.LINEAR_WEIGHTS, NgramLanguageDetectorForTests.get(), 4);

		StringBuilder sb = new StringBuilder();
		sb.append(BaseSingleLangTest.getEnglishString());
		sb.append(BaseSingleLangTest.getFrenchString());
		sb.append(BaseSingleLangTest.getItalianString());
		sb.append(BaseSingleLangTest.getGermanString());
		sb.append(BaseSingleLangTest.getSpanishString());
		sb.append(BaseSingleLangTest.getPortugueseString());
		List<Pair<String, Locale>> tags = boundaryDetector.tagStringWithLanguages(sb.toString());

		assertEquals(6, tags.size(), "Should have returned 6 phrases");

		assertEquals(Locale.ENGLISH, tags.get(0).second(), "Didn't match language");
		assertEquals(Locale.FRENCH, tags.get(1).second(), "Didn't match language");
		assertEquals(Locale.ITALIAN, tags.get(2).second(), "Didn't match language");
		assertEquals(Locale.GERMAN, tags.get(3).second(), "Didn't match language");
		assertEquals(Locale.of("es"), tags.get(4).second(), "Didn't match language");
		assertEquals(Locale.of("pt"), tags.get(5).second(), "Didn't match language");

	}

	// for larger string only checks the order, doesn't check the boundaries
	@Test
	void testLargeMultiLangOrder() throws Exception {

		LanguageBoundaryDetector boundaryDetector = new SlidingWindowBigramBoundaryDetector(
				ClassificationAlgorithm.LINEAR_WEIGHTS, NgramLanguageDetectorForTests.get(), 4);

		final int numRepetitions = 100;

		StringBuilder sb = new StringBuilder(65536);
		for (int ind = 0; ind < numRepetitions; ind++) {
			sb.append(BaseSingleLangTest.getEnglishString());
			sb.append(BaseSingleLangTest.getFrenchString());
			sb.append(BaseSingleLangTest.getItalianString());
			sb.append(BaseSingleLangTest.getGermanString());
			sb.append(BaseSingleLangTest.getSpanishString());
			sb.append(BaseSingleLangTest.getPortugueseString());
		}
		List<Pair<String, Locale>> tags = boundaryDetector.tagStringWithLanguages(sb.toString());

		assertEquals(6 * numRepetitions, tags.size(), "Should have returned " + (6 * numRepetitions) + " phrases");

		for (int ind = 0; ind < numRepetitions; ind++) {
			assertEquals(Locale.ENGLISH, tags.get(ind * 6).second(), "Didn't match language");
			assertEquals(Locale.FRENCH, tags.get(ind * 6 + 1).second(), "Didn't match language");
			assertEquals(Locale.ITALIAN, tags.get(ind * 6 + 2).second(), "Didn't match language");
			assertEquals(Locale.GERMAN, tags.get(ind * 6 + 3).second(), "Didn't match language");
			assertEquals(Locale.of("es"), tags.get(ind * 6 + 4).second(), "Didn't match language");
			assertEquals(Locale.of("pt"), tags.get(ind * 6 + 5).second(), "Didn't match language");
		}

	}

}
