package language.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;

import org.junit.jupiter.api.Test;

/**
 * Serializing detection results, as the CLI and multi-language reports do
 *
 * @author Andrey Gusev
 */
class LanguageDetectorTest {

	// an undetermined result must print as "und", not as Locale.ROOT's empty toString()
	@Test
	void testLanguageTag() {
		assertEquals("und", LanguageDetector.languageTag(LanguageDetector.UNDETERMINED), "Undetermined tag");
		assertEquals("und", LanguageDetector.languageTag(null), "No-result tag");
		assertEquals("en", LanguageDetector.languageTag(Locale.ENGLISH), "English tag");
		assertEquals("pt", LanguageDetector.languageTag(Locale.of("pt")), "Portuguese tag");
	}
}
