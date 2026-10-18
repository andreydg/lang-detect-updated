package language.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import language.model.NgramLanguageDetector.ClassificationAlgorithm;
import language.model.multiling.BigramBoundaryDetector;
import language.model.multiling.LanguageBoundaryDetector;
import language.model.multiling.NestedSlidingWindowBigramBoundaryDetector;
import language.model.multiling.SlidingWindowBigramBoundaryDetector;
import language.util.Pair;

/**
 * Test multi-lang strings
 *
 * @author Andrey Gusev
 */
class MultiLangTest {

	private static final String RUSSIAN = "Это тестовая строка для определения языка текста.";

	private static final String NEUTRAL_RUN_IN_ENGLISH =
			"The invoice numbers are listed in the . . . . table below for your reference";

	private static LanguageBoundaryDetector newBoundaryDetector() throws Exception {
		return new SlidingWindowBigramBoundaryDetector(ClassificationAlgorithm.LINEAR_WEIGHTS,
				NgramLanguageDetectorForTests.get(), 4);
	}

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

	// text in none of the supported languages is tagged as undetermined, not guessed
	@Test
	void testUnsupportedTextIsUndetermined() throws Exception {

		LanguageBoundaryDetector boundaryDetector = newBoundaryDetector();

		for (String text : List.of(RUSSIAN, "1 2 3 4 " + RUSSIAN)) {
			assertEquals(List.of(new Pair<>(text, LanguageDetector.UNDETERMINED)),
					boundaryDetector.tagStringWithLanguages(text), "Should be one undetermined phrase");
		}
	}

	// input of nothing but digits and punctuation is undetermined as well
	@Test
	void testNeutralInputIsUndetermined() throws Exception {

		LanguageBoundaryDetector boundaryDetector = newBoundaryDetector();

		for (String text : List.of("1234 5678 9012 3456 7890", ". . . . . . . .")) {
			assertEquals(List.of(new Pair<>(text, LanguageDetector.UNDETERMINED)),
					boundaryDetector.tagStringWithLanguages(text), "Should be one undetermined phrase");
		}
	}

	// digits and punctuation have no language of their own, so they stay in the
	// segment around them instead of splitting it
	@Test
	void testNeutralRunsStayInTheirSegment() throws Exception {

		LanguageBoundaryDetector boundaryDetector = newBoundaryDetector();

		List<Pair<String, Locale>> expectedSegments = List.of(
				new Pair<>(NEUTRAL_RUN_IN_ENGLISH, Locale.ENGLISH),
				new Pair<>("Please call me at 555 0100 1234 5678 tomorrow morning about the invoice", Locale.ENGLISH),
				new Pair<>("1 2 3 4 This is a test string for language detection", Locale.ENGLISH),
				// used to come out as Spanish / "English" 1 2 3 / Spanish
				new Pair<>("y doy 1 2 3 4 fe que habiendo visto", Locale.of("es")),
				// used to come out as French / "English" 1 2 3 4
				new Pair<>("un navire parfaitement gouverné son ancre 1 2 3 4", Locale.FRENCH));
		for (Pair<String, Locale> expected : expectedSegments) {
			assertEquals(List.of(expected), boundaryDetector.tagStringWithLanguages(expected.first()),
					"Neutral run changed the segmentation");
		}
	}

	// the nested detector shares the window and segment rules: its output on the
	// six phrases is the same as before them (it misses the German phrase)
	@Test
	void testNestedDetector() throws Exception {

		LanguageBoundaryDetector boundaryDetector = new NestedSlidingWindowBigramBoundaryDetector(
				ClassificationAlgorithm.LINEAR_WEIGHTS, NgramLanguageDetectorForTests.get(), 5);

		assertEquals(List.of(Locale.ENGLISH, Locale.FRENCH, Locale.ITALIAN, Locale.of("es"), Locale.of("pt")),
				languagesOf(boundaryDetector.tagStringWithLanguages(sixPhrases())), "Didn't match languages");
		assertEquals(List.of(new Pair<>(NEUTRAL_RUN_IN_ENGLISH, Locale.ENGLISH)),
				boundaryDetector.tagStringWithLanguages(NEUTRAL_RUN_IN_ENGLISH), "Neutral run split the segment");
	}

	// the base bigram detector used to throw on single-word input
	@Test
	void testBaseDetectorSingleWord() throws Exception {

		LanguageBoundaryDetector boundaryDetector = new BigramBoundaryDetector(
				ClassificationAlgorithm.LINEAR_WEIGHTS, NgramLanguageDetectorForTests.get());

		assertEquals(List.of(new Pair<>("Bonjour", Locale.FRENCH)), boundaryDetector.tagStringWithLanguages("Bonjour"),
				"Should be one phrase");
	}

	private static String sixPhrases() {
		return BaseSingleLangTest.getEnglishString() + BaseSingleLangTest.getFrenchString()
				+ BaseSingleLangTest.getItalianString() + BaseSingleLangTest.getGermanString()
				+ BaseSingleLangTest.getSpanishString() + BaseSingleLangTest.getPortugueseString();
	}

	// unsupported text next to a supported language becomes its own segment
	// (only the order is checked, not the exact boundary)
	@Test
	void testUnsupportedTextGetsItsOwnSegment() throws Exception {

		LanguageBoundaryDetector boundaryDetector = newBoundaryDetector();

		List<Pair<String, Locale>> tags = boundaryDetector
				.tagStringWithLanguages(BaseSingleLangTest.getEnglishString() + ". " + RUSSIAN);
		assertEquals(List.of(Locale.ENGLISH, LanguageDetector.UNDETERMINED), languagesOf(tags),
				"Didn't match languages");

		tags = boundaryDetector.tagStringWithLanguages(RUSSIAN + " " + BaseSingleLangTest.getFrenchString());
		assertEquals(List.of(LanguageDetector.UNDETERMINED, Locale.FRENCH), languagesOf(tags),
				"Didn't match languages");
	}

	private static List<Locale> languagesOf(List<Pair<String, Locale>> tags) {
		return tags.stream().map(Pair::second).toList();
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
