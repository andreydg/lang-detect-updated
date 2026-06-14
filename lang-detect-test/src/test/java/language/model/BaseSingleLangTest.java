package language.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;

import language.model.NgramLanguageDetector.ClassificationAlgorithm;

/**
 * Single language test
 *
 * @author Andrey Gusev
 */
public abstract class BaseSingleLangTest {

	private final LanguageDetector detector = NgramLanguageDetectorForTests.get();

	// basic check
	protected void _testBasicPhrase(ClassificationAlgorithm type) throws Exception {

		assertEquals(Locale.ENGLISH, detector.getMostLikelyLanguage(getEnglishString(), type), "Didn't match language");

		assertEquals(Locale.FRENCH, detector.getMostLikelyLanguage(getFrenchString(), type), "Didn't match language");

		assertEquals(Locale.ITALIAN, detector.getMostLikelyLanguage(getItalianString(), type), "Didn't match language");

		assertEquals(Locale.GERMAN, detector.getMostLikelyLanguage(getGermanString(), type), "Didn't match language");

		assertEquals(Locale.of("es"), detector.getMostLikelyLanguage(getSpanishString(), type), "Didn't match language");

		assertEquals(
				Locale.of("pt"), detector.getMostLikelyLanguage(getPortugueseString(), type), "Didn't match language");

	}

	// check longer strings
	protected void _testLargeEnglishString(ClassificationAlgorithm type) throws Exception {

		StringBuilder sb = new StringBuilder(65536);
		for (int ind = 0; ind < 1000; ind++) {
			sb.append(getEnglishString()).append(" ");
		}

		assertEquals(Locale.ENGLISH, detector.getMostLikelyLanguage(sb.toString(), type), "Didn't match language");
	}

	protected static String getEnglishString() {
		return "This is a test string for language detection";
	}

	protected static String getFrenchString() {
		return "Il s'agit d'une chaîne de test pour la détection de la langue";
	}

	protected static String getItalianString() {
		return "Questa è una stringa di prova per il rilevamento della lingua";
	}

	protected static String getGermanString() {
		return "Dies ist ein Test-String für Spracherkennung";
	}

	protected static String getSpanishString() {
		return "Esta es una cadena de prueba para la detección de idioma";
	}

	protected static String getPortugueseString() {
		return "Esta é uma seqüência de teste para detecção de idioma";
	}

}
